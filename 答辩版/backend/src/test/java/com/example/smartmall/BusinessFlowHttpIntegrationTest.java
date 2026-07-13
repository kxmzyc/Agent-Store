package com.example.smartmall;

import com.example.smartmall.domain.Category;
import com.example.smartmall.domain.User;
import com.example.smartmall.repo.CategoryRepository;
import com.example.smartmall.repo.UserRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.time.LocalDateTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BusinessFlowHttpIntegrationTest {
  private static final Path UPLOAD_DIR = createUploadDirectory();
  @LocalServerPort int port;
  @Autowired UserRepository users;
  @Autowired CategoryRepository categories;
  @Autowired PasswordEncoder passwordEncoder;

  @BeforeEach
  void configureHttpClient() {
    RestAssured.port = port;
  }

  @DynamicPropertySource
  static void configureUploadDirectory(DynamicPropertyRegistry registry) {
    registry.add("app.upload-dir", () -> UPLOAD_DIR.toString());
  }

  @Test
  void registrationToCompletedOrderFlowUsesRealHttpContracts() {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    String username = "flow_" + suffix;
    String phone = "139" + Math.floorMod(suffix.hashCode(), 100_000_000);
    String adminName = "flow_admin_" + suffix;
    String address = "HTTP integration " + suffix;
    Category category = category(suffix);
    String adminToken = adminToken(adminName, suffix);

    given().get("/api/user/profile").then().statusCode(401);

    given().contentType(ContentType.JSON)
        .body(Map.of("username", username, "password", "secret123", "phone", phone))
        .post("/api/auth/register")
        .then().statusCode(201).body("userId", notNullValue()).body("username", equalTo(username));

    given().contentType(ContentType.JSON)
        .body(Map.of("username", username, "password", "secret123", "phone", phone))
        .post("/api/auth/register")
        .then().statusCode(409);

    var login = given().contentType(ContentType.JSON)
        .body(Map.of("username", username, "password", "secret123"))
        .post("/api/auth/login")
        .then().statusCode(200).body("accessToken", notNullValue()).body("refreshToken", notNullValue())
        .extract();
    String userToken = login.path("accessToken");
    String refreshToken = login.path("refreshToken");

    given().contentType(ContentType.JSON).body(Map.of("refreshToken", refreshToken))
        .post("/api/auth/refresh").then().statusCode(200).body("accessToken", notNullValue());
    given().contentType(ContentType.JSON).body(Map.of("refreshToken", "not-a-token"))
        .post("/api/auth/refresh").then().statusCode(401);

    Map<String, Object> productRequest = productRequest(category.id, "HTTP keyboard " + suffix, 3);
    given().auth().oauth2(userToken).contentType(ContentType.JSON).body(productRequest)
        .post("/api/products").then().statusCode(403);

    Map<String, Object> negativePriceRequest = productRequest(category.id, "HTTP invalid price " + suffix, 3);
    negativePriceRequest.put("price", "-1.00");
    given().auth().oauth2(adminToken).contentType(ContentType.JSON).body(negativePriceRequest)
        .post("/api/products").then().statusCode(400);

    Map<String, Object> negativeStockRequest = productRequest(category.id, "HTTP invalid stock " + suffix, 3);
    negativeStockRequest.put("stock", -1);
    given().auth().oauth2(adminToken).contentType(ContentType.JSON).body(negativeStockRequest)
        .post("/api/products").then().statusCode(400);

    Long productId = number(given().auth().oauth2(adminToken).contentType(ContentType.JSON).body(productRequest)
        .post("/api/products").then().statusCode(201).body("name", equalTo(productRequest.get("name")))
        .extract().path("id"));

    String imageUrl = given().auth().oauth2(adminToken)
        .multiPart("file", "cover.png", new byte[] {1, 2, 3}, "image/png")
        .post("/api/products/upload-image").then().statusCode(201).extract().path("imageUrl");
    given().get(imageUrl).then().statusCode(200).contentType("image/png");

    given().queryParam("keyword", "HTTP keyboard").queryParam("page", 1).queryParam("size", 20)
        .get("/api/products/search")
        .then().statusCode(200).body("total", greaterThanOrEqualTo(1)).body("list[0].id", equalTo(productId.intValue()));
    given().get("/api/products?page=9999&size=20").then().statusCode(200).body("list", empty());

    Long cartId = number(given().auth().oauth2(userToken).contentType(ContentType.JSON)
        .body(Map.of("productId", productId, "quantity", 1))
        .post("/api/cart").then().statusCode(201).body("productId", equalTo(productId.intValue()))
        .extract().path("id"));

    Long orderId = number(given().auth().oauth2(userToken).contentType(ContentType.JSON)
        .body(Map.of("cartItemIds", List.of(cartId), "shippingAddress", address, "usePoints", false))
        .post("/api/orders").then().statusCode(201).body("orderId", notNullValue()).body("orderNo", notNullValue())
        .extract().path("orderId"));

    given().auth().oauth2(userToken).get("/api/orders/" + orderId)
        .then().statusCode(200).body("status", equalTo("PENDING_PAYMENT")).body("items.size()", equalTo(1));
    given().auth().oauth2(userToken).put("/api/orders/" + orderId + "/pay")
        .then().statusCode(200).body("status", equalTo("PAID"));
    given().auth().oauth2(adminToken).put("/api/orders/" + orderId + "/ship")
        .then().statusCode(200).body("status", equalTo("SHIPPED"));
    given().auth().oauth2(userToken).put("/api/orders/" + orderId + "/confirm")
        .then().statusCode(200).body("status", equalTo("COMPLETED"));
    given().auth().oauth2(userToken).put("/api/orders/" + orderId + "/pay")
        .then().statusCode(400);

    Long soldOutProductId = number(given().auth().oauth2(adminToken).contentType(ContentType.JSON)
        .body(productRequest(category.id, "HTTP sold out " + suffix, 0))
        .post("/api/products").then().statusCode(201).extract().path("id"));
    given().auth().oauth2(userToken).contentType(ContentType.JSON)
        .body(Map.of("productId", soldOutProductId, "quantity", 1))
        .post("/api/cart").then().statusCode(400);

    User registered = users.findByUsername(username).orElseThrow();
    registered.status = 0;
    users.save(registered);
    given().contentType(ContentType.JSON).body(Map.of("refreshToken", refreshToken))
        .post("/api/auth/refresh").then().statusCode(401);
  }

  private String adminToken(String username, String suffix) {
    User admin = new User();
    admin.username = username;
    admin.passwordHash = passwordEncoder.encode("admin123");
    admin.phone = "138" + Math.floorMod(("admin" + suffix).hashCode(), 100_000_000);
    admin.role = "ADMIN";
    admin.status = 1;
    admin.points = 0;
    admin.createdAt = LocalDateTime.now();
    admin.updatedAt = admin.createdAt;
    users.save(admin);
    return given().contentType(ContentType.JSON)
        .body(Map.of("username", username, "password", "admin123"))
        .post("/api/auth/login").then().statusCode(200).extract().path("accessToken");
  }

  private Category category(String suffix) {
    Category category = new Category();
    category.name = "HTTP category " + suffix;
    category.parentId = null;
    category.sortOrder = 0;
    return categories.save(category);
  }

  private Map<String, Object> productRequest(Long categoryId, String name, int stock) {
    return new HashMap<>(Map.of(
        "categoryId", categoryId,
        "name", name,
        "description", "HTTP integration product",
        "price", "99.00",
        "stock", stock,
        "imageUrl", "",
        "status", 1,
        "tagIds", List.of()));
  }

  private Long number(Object value) {
    return ((Number) value).longValue();
  }

  private static Path createUploadDirectory() {
    try {
      return Files.createTempDirectory("smart-mall-http-upload-");
    } catch (Exception ex) {
      throw new IllegalStateException("Cannot create HTTP test upload directory", ex);
    }
  }
}

package com.example.smartmall;

import com.example.smartmall.domain.Product;
import com.example.smartmall.domain.User;
import com.example.smartmall.repo.ProductRepository;
import com.example.smartmall.repo.UserRepository;
import com.example.smartmall.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CartControllerIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired UserRepository users;
  @Autowired ProductRepository products;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired JwtService jwtService;

  @Test
  void addUpdateDeleteCartItem() throws Exception {
    User user = user("cart_user");
    Product product = product("Cart Test Keyboard", 8);
    String token = jwtService.accessToken(user.id, user.username, user.role);

    String addBody = mvc.perform(post("/api/cart")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("productId", product.id, "quantity", 2))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.productId").value(product.id))
        .andExpect(jsonPath("$.quantity").value(2))
        .andReturn().getResponse().getContentAsString();
    long cartId = objectMapper.readTree(addBody).get("id").asLong();

    mvc.perform(get("/api/cart").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].productId").value(product.id));

    mvc.perform(put("/api/cart/{id}", cartId)
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("quantity", 3))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.quantity").value(3));

    mvc.perform(delete("/api/cart/{id}", cartId).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ok").value(true));
  }

  @Test
  void unauthenticatedCartAccessIsRejected() throws Exception {
    mvc.perform(get("/api/cart")).andExpect(status().isUnauthorized());
  }

  private String json(Object value) throws Exception {
    return objectMapper.writeValueAsString(value);
  }

  private User user(String prefix) {
    User user = new User();
    user.username = prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    user.passwordHash = passwordEncoder.encode("secret123");
    user.phone = "139" + String.format("%08d", Math.floorMod(user.username.hashCode(), 100_000_000));
    user.role = "USER";
    user.status = 1;
    user.points = 0;
    user.createdAt = LocalDateTime.now();
    user.updatedAt = user.createdAt;
    return users.save(user);
  }

  private Product product(String name, int stock) {
    Product product = new Product();
    product.categoryId = 1L;
    product.name = name + " " + UUID.randomUUID().toString().substring(0, 8);
    product.description = "cart integration test product";
    product.price = new BigDecimal("99.00");
    product.stock = stock;
    product.salesCount = 0;
    product.status = 1;
    product.version = 0;
    product.createdAt = LocalDateTime.now();
    return products.save(product);
  }
}

package com.example.smartmall;

import com.example.smartmall.api.BizException;
import com.example.smartmall.api.ApiSupport.AfterSaleApplyRequest;
import com.example.smartmall.api.ApiSupport.AfterSaleHandleRequest;
import com.example.smartmall.api.ApiSupport.CreateOrderRequest;
import com.example.smartmall.repo.AfterSaleRequestRepository;
import com.example.smartmall.domain.CartItem;
import com.example.smartmall.domain.Coupon;
import com.example.smartmall.domain.Product;
import com.example.smartmall.domain.User;
import com.example.smartmall.domain.UserCoupon;
import com.example.smartmall.repo.CartRepository;
import com.example.smartmall.repo.CouponRepository;
import com.example.smartmall.repo.OrderRepository;
import com.example.smartmall.repo.ProductRepository;
import com.example.smartmall.repo.UserCouponRepository;
import com.example.smartmall.repo.UserRepository;
import com.example.smartmall.service.AfterSaleService;
import com.example.smartmall.service.OrderService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class OrderServiceIntegrationTest {
  @Autowired OrderService orderService;
  @Autowired AfterSaleService afterSaleService;
  @Autowired UserRepository users;
  @Autowired ProductRepository products;
  @Autowired CartRepository carts;
  @Autowired OrderRepository orders;
  @Autowired AfterSaleRequestRepository afterSales;
  @Autowired CouponRepository coupons;
  @Autowired UserCouponRepository userCoupons;
  @Autowired PasswordEncoder passwordEncoder;

  @Test
  void createOrderDeductsStock() {
    User user = user("order_user");
    Product product = product("Stock Deduct Product", "120.00", 5);
    CartItem cart = cart(user.id, product.id, 2);

    var response = orderService.create(user.id, new CreateOrderRequest(List.of(cart.id), "test address", null, false));

    Product updated = products.findById(product.id).orElseThrow();
    assertThat(response.totalAmount()).isEqualByComparingTo("240.00");
    assertThat(updated.stock).isEqualTo(3);
    assertThat(carts.findById(cart.id)).isEmpty();
  }

  @Test
  void concurrentDirectOrdersDoNotOversell() throws Exception {
    User user = user("race_user");
    Product product = product("Concurrent Product", "9.90", 5);
    int attempts = 12;
    var pool = Executors.newFixedThreadPool(attempts);
    CountDownLatch ready = new CountDownLatch(attempts);
    CountDownLatch start = new CountDownLatch(1);
    AtomicInteger success = new AtomicInteger();

    for (int i = 0; i < attempts; i++) {
      pool.submit(() -> {
        ready.countDown();
        try {
          start.await(3, TimeUnit.SECONDS);
          orderService.createDirect(user.id, product.id, 1, "test address", null, false);
          success.incrementAndGet();
        } catch (Exception ignored) {
          // Stock conflicts are the expected losing path in this race.
        }
      });
    }
    ready.await(3, TimeUnit.SECONDS);
    start.countDown();
    pool.shutdown();
    assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

    Product updated = products.findById(product.id).orElseThrow();
    assertThat(success.get()).isLessThanOrEqualTo(5);
    assertThat(updated.stock).isEqualTo(5 - success.get());
    assertThat(updated.stock).isGreaterThanOrEqualTo(0);
  }

  @Test
  void cancelOrderRestoresStock() {
    User user = user("cancel_user");
    Product product = product("Cancel Restore Product", "88.00", 4);

    var created = orderService.createDirect(user.id, product.id, 2, "test address", null, false);
    assertThat(products.findById(product.id).orElseThrow().stock).isEqualTo(2);

    orderService.cancel(user.id, created.orderId());

    assertThat(products.findById(product.id).orElseThrow().stock).isEqualTo(4);
    assertThat(orders.findById(created.orderId()).orElseThrow().status).isEqualTo("CANCELLED");
  }

  @Test
  void couponOrderCalculatesAmountAndMarksCouponUsed() {
    User user = user("coupon_order_user");
    Product product = product("Coupon Product", "100.00", 3);
    UserCoupon userCoupon = userCoupon(user.id, coupon("Test Coupon", 1, "50.00", "10.00"));

    var created = orderService.createDirect(user.id, product.id, 1, "test address", userCoupon.id, false);

    assertThat(created.totalAmount()).isEqualByComparingTo("90.00");
    assertThat(created.discountAmount()).isEqualByComparingTo("10.00");
    assertThat(userCoupons.findById(userCoupon.id).orElseThrow().status).isEqualTo(1);
  }

  @Test
  void approvedAfterSaleRestoresStockCouponAndMarksOrderRefunded() {
    User user = user("after_sale_user");
    Product product = product("Refundable Product", "100.00", 3);
    UserCoupon userCoupon = userCoupon(user.id, coupon("Refund Coupon", 1, "50.00", "10.00"));

    var created = orderService.createDirect(user.id, product.id, 1, "test address", userCoupon.id, false);
    orderService.pay(user.id, created.orderId());
    orderService.ship(created.orderId());

    var applied = afterSaleService.apply(user.id, created.orderId(), new AfterSaleApplyRequest(2, "尺码不合适，申请退货退款"));
    var approved = afterSaleService.approve(applied.id(), new AfterSaleHandleRequest("同意退货退款"));

    Product updated = products.findById(product.id).orElseThrow();
    UserCoupon updatedCoupon = userCoupons.findById(userCoupon.id).orElseThrow();

    assertThat(approved.status()).isEqualTo(3);
    assertThat(afterSales.findById(applied.id()).orElseThrow().status).isEqualTo(3);
    assertThat(orders.findById(created.orderId()).orElseThrow().status).isEqualTo("REFUNDED");
    assertThat(updated.stock).isEqualTo(3);
    assertThat(updatedCoupon.status).isEqualTo(0);
    assertThat(updatedCoupon.usedOrderId).isNull();
  }

  private CartItem cart(Long userId, Long productId, int quantity) {
    CartItem item = new CartItem();
    item.userId = userId;
    item.productId = productId;
    item.quantity = quantity;
    item.createdAt = LocalDateTime.now();
    return carts.save(item);
  }

  private User user(String prefix) {
    User user = new User();
    user.username = prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    user.passwordHash = passwordEncoder.encode("secret123");
    user.phone = "137" + String.format("%08d", Math.floorMod(user.username.hashCode(), 100_000_000));
    user.role = "USER";
    user.status = 1;
    user.points = 0;
    user.createdAt = LocalDateTime.now();
    user.updatedAt = user.createdAt;
    return users.save(user);
  }

  private Product product(String name, String price, int stock) {
    Product product = new Product();
    product.categoryId = 1L;
    product.name = name + " " + UUID.randomUUID().toString().substring(0, 8);
    product.description = "order integration test product";
    product.price = new BigDecimal(price);
    product.stock = stock;
    product.salesCount = 0;
    product.status = 1;
    product.version = 0;
    product.createdAt = LocalDateTime.now();
    return products.save(product);
  }

  private Coupon coupon(String name, int type, String threshold, String discount) {
    Coupon coupon = new Coupon();
    coupon.name = name + " " + UUID.randomUUID().toString().substring(0, 8);
    coupon.type = type;
    coupon.threshold = new BigDecimal(threshold);
    coupon.discount = new BigDecimal(discount);
    coupon.totalCount = 100;
    coupon.remainCount = 100;
    coupon.validDays = 30;
    coupon.createdAt = LocalDateTime.now();
    return coupons.save(coupon);
  }

  private UserCoupon userCoupon(Long userId, Coupon coupon) {
    UserCoupon userCoupon = new UserCoupon();
    userCoupon.userId = userId;
    userCoupon.couponId = coupon.id;
    userCoupon.status = 0;
    userCoupon.expireAt = LocalDate.now().plusDays(30);
    userCoupon.createdAt = LocalDateTime.now();
    return userCoupons.save(userCoupon);
  }
}

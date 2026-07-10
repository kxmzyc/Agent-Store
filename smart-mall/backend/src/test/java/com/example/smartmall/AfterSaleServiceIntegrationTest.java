package com.example.smartmall;

import com.example.smartmall.api.ApiSupport.AfterSaleApplyRequest;
import com.example.smartmall.api.ApiSupport.AfterSaleHandleRequest;
import com.example.smartmall.api.BizException;
import com.example.smartmall.domain.Coupon;
import com.example.smartmall.domain.Product;
import com.example.smartmall.domain.User;
import com.example.smartmall.domain.UserCoupon;
import com.example.smartmall.repo.AfterSaleRequestRepository;
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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class AfterSaleServiceIntegrationTest {
  @Autowired AfterSaleService afterSaleService;
  @Autowired OrderService orderService;
  @Autowired UserRepository users;
  @Autowired ProductRepository products;
  @Autowired OrderRepository orders;
  @Autowired AfterSaleRequestRepository afterSales;
  @Autowired CouponRepository coupons;
  @Autowired UserCouponRepository userCoupons;
  @Autowired PasswordEncoder passwordEncoder;

  @Test
  void refundOnlyApprovalRefundsOrderAndCouponWithoutRestoringStock() {
    User user = user("refund_only_user");
    Product product = product("Refund Only Product", "100.00", 5);
    UserCoupon userCoupon = userCoupon(user.id, coupon("Refund Only Coupon", "50.00", "10.00"));

    var created = orderService.createDirect(user.id, product.id, 2, "test address", userCoupon.id, false);
    orderService.pay(user.id, created.orderId());
    orderService.ship(created.orderId());
    assertThat(products.findById(product.id).orElseThrow().stock).isEqualTo(3);

    var applied = afterSaleService.apply(user.id, created.orderId(), new AfterSaleApplyRequest(1, "refund only"));
    var approved = afterSaleService.approve(applied.id(), new AfterSaleHandleRequest("approved"));

    Product updated = products.findById(product.id).orElseThrow();
    UserCoupon updatedCoupon = userCoupons.findById(userCoupon.id).orElseThrow();
    assertThat(approved.status()).isEqualTo(3);
    assertThat(orders.findById(created.orderId()).orElseThrow().status).isEqualTo("REFUNDED");
    assertThat(updated.stock).isEqualTo(3);
    assertThat(updatedCoupon.status).isEqualTo(0);
    assertThat(updatedCoupon.usedOrderId).isNull();
  }

  @Test
  void returnRefundApprovalRefundsOrderRestoresStockAndCoupon() {
    User user = user("return_refund_user");
    Product product = product("Return Refund Product", "120.00", 4);
    UserCoupon userCoupon = userCoupon(user.id, coupon("Return Refund Coupon", "50.00", "15.00"));

    var created = orderService.createDirect(user.id, product.id, 1, "test address", userCoupon.id, false);
    orderService.pay(user.id, created.orderId());
    orderService.ship(created.orderId());
    assertThat(products.findById(product.id).orElseThrow().stock).isEqualTo(3);

    var applied = afterSaleService.apply(user.id, created.orderId(), new AfterSaleApplyRequest(2, "return refund"));
    var approved = afterSaleService.approve(applied.id(), new AfterSaleHandleRequest("approved"));

    Product updated = products.findById(product.id).orElseThrow();
    UserCoupon updatedCoupon = userCoupons.findById(userCoupon.id).orElseThrow();
    assertThat(approved.status()).isEqualTo(3);
    assertThat(orders.findById(created.orderId()).orElseThrow().status).isEqualTo("REFUNDED");
    assertThat(updated.stock).isEqualTo(4);
    assertThat(updatedCoupon.status).isEqualTo(0);
    assertThat(updatedCoupon.usedOrderId).isNull();
  }

  @Test
  void rejectLeavesOrderStockAndCouponUnchanged() {
    User user = user("reject_after_sale_user");
    Product product = product("Reject Product", "80.00", 3);
    UserCoupon userCoupon = userCoupon(user.id, coupon("Reject Coupon", "50.00", "8.00"));

    var created = orderService.createDirect(user.id, product.id, 1, "test address", userCoupon.id, false);
    orderService.pay(user.id, created.orderId());
    orderService.ship(created.orderId());

    var applied = afterSaleService.apply(user.id, created.orderId(), new AfterSaleApplyRequest(2, "return refund"));
    var rejected = afterSaleService.reject(applied.id(), new AfterSaleHandleRequest("not eligible"));

    Product updated = products.findById(product.id).orElseThrow();
    UserCoupon updatedCoupon = userCoupons.findById(userCoupon.id).orElseThrow();
    assertThat(rejected.status()).isEqualTo(2);
    assertThat(orders.findById(created.orderId()).orElseThrow().status).isEqualTo("SHIPPED");
    assertThat(updated.stock).isEqualTo(2);
    assertThat(updatedCoupon.status).isEqualTo(1);
    assertThat(updatedCoupon.usedOrderId).isEqualTo(created.orderId());
  }

  @Test
  void cannotApplyAfterSaleBeforeOrderIsShipped() {
    User user = user("invalid_status_user");
    Product product = product("Invalid Status Product", "60.00", 2);

    var created = orderService.createDirect(user.id, product.id, 1, "test address", null, false);

    assertThatThrownBy(() ->
        afterSaleService.apply(user.id, created.orderId(), new AfterSaleApplyRequest(2, "too early")))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("暂不支持");
    assertThat(afterSales.findAll().stream().noneMatch(request -> request.orderId.equals(created.orderId()))).isTrue();
  }

  @Test
  void duplicateOpenAfterSaleRequestIsRejected() {
    User user = user("duplicate_after_sale_user");
    Product product = product("Duplicate Product", "90.00", 3);

    var created = orderService.createDirect(user.id, product.id, 1, "test address", null, false);
    orderService.pay(user.id, created.orderId());
    orderService.ship(created.orderId());
    afterSaleService.apply(user.id, created.orderId(), new AfterSaleApplyRequest(1, "first request"));

    assertThatThrownBy(() ->
        afterSaleService.apply(user.id, created.orderId(), new AfterSaleApplyRequest(1, "second request")))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("已有售后记录");
    assertThat(afterSales.findAll().stream().filter(request -> request.orderId.equals(created.orderId())).count())
        .isEqualTo(1);
  }

  private User user(String prefix) {
    User user = new User();
    user.username = prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    user.passwordHash = passwordEncoder.encode("secret123");
    user.phone = "135" + String.format("%08d", Math.floorMod(user.username.hashCode(), 100_000_000));
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
    product.description = "after sale integration test product";
    product.price = new BigDecimal(price);
    product.stock = stock;
    product.salesCount = 0;
    product.status = 1;
    product.version = 0;
    product.createdAt = LocalDateTime.now();
    return products.save(product);
  }

  private Coupon coupon(String name, String threshold, String discount) {
    Coupon coupon = new Coupon();
    coupon.name = name + " " + UUID.randomUUID().toString().substring(0, 8);
    coupon.type = 1;
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

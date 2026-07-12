package com.example.smartmall;

import com.example.smartmall.api.ApiSupport.AfterSaleApplyRequest;
import com.example.smartmall.api.ApiSupport.AfterSaleHandleRequest;
import com.example.smartmall.api.BizException;
import com.example.smartmall.domain.Coupon;
import com.example.smartmall.domain.PointRecord;
import com.example.smartmall.domain.Product;
import com.example.smartmall.domain.User;
import com.example.smartmall.domain.UserCoupon;
import com.example.smartmall.repo.AfterSaleRequestRepository;
import com.example.smartmall.repo.CouponRepository;
import com.example.smartmall.repo.OrderRepository;
import com.example.smartmall.repo.PointRecordRepository;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
  @Autowired PointRecordRepository pointRecords;
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

  @Test
  void completedOrderRefundReversesAwardedPointsOnlyOnce() {
    User user = user("completed_refund_points_user");
    Product product = product("Completed Refund Points Product", "120.00", 2);
    var created = orderService.createDirect(user.id, product.id, 1, "test address", null, false);
    orderService.pay(user.id, created.orderId());
    orderService.ship(created.orderId());
    orderService.confirm(user.id, created.orderId());

    assertThat(users.findById(user.id).orElseThrow().points).isEqualTo(120);
    var applied = afterSaleService.apply(user.id, created.orderId(), new AfterSaleApplyRequest(1, "refund points"));
    afterSaleService.approve(applied.id(), new AfterSaleHandleRequest("approved"));

    String reversalReason = "退款订单 " + created.orderNo() + " 积分回滚";
    List<PointRecord> reversals = pointRecords.findByUserIdAndReason(user.id, reversalReason);
    assertThat(users.findById(user.id).orElseThrow().points).isZero();
    assertThat(reversals).singleElement().extracting(record -> record.delta).isEqualTo(-120);
    assertThatThrownBy(() -> afterSaleService.approve(applied.id(), new AfterSaleHandleRequest("duplicate")))
        .isInstanceOf(BizException.class);
    assertThat(pointRecords.findByUserIdAndReason(user.id, reversalReason)).hasSize(1);
  }

  @Test
  void completedOrderRefundNeverMakesPointsNegative() {
    User user = user("completed_refund_low_points_user");
    Product product = product("Completed Refund Low Points Product", "100.00", 2);
    var created = orderService.createDirect(user.id, product.id, 1, "test address", null, false);
    orderService.pay(user.id, created.orderId());
    orderService.ship(created.orderId());
    orderService.confirm(user.id, created.orderId());

    User afterSpendingPoints = users.findById(user.id).orElseThrow();
    afterSpendingPoints.points = 25;
    users.save(afterSpendingPoints);
    var applied = afterSaleService.apply(user.id, created.orderId(), new AfterSaleApplyRequest(1, "refund remaining points"));
    afterSaleService.approve(applied.id(), new AfterSaleHandleRequest("approved"));

    List<PointRecord> reversals = pointRecords.findByUserIdAndReason(user.id,
        "退款订单 " + created.orderNo() + " 积分回滚");
    assertThat(users.findById(user.id).orElseThrow().points).isZero();
    assertThat(reversals).singleElement().extracting(record -> record.delta).isEqualTo(-25);
  }

  @Test
  void concurrentApprovalsApplyRefundAndPointReversalOnlyOnce() throws Exception {
    User user = user("concurrent_refund_user");
    Product product = product("Concurrent Refund Product", "100.00", 1);
    var created = orderService.createDirect(user.id, product.id, 1, "test address", null, false);
    orderService.pay(user.id, created.orderId());
    orderService.ship(created.orderId());
    orderService.confirm(user.id, created.orderId());
    var applied = afterSaleService.apply(user.id, created.orderId(), new AfterSaleApplyRequest(2, "concurrent refund"));

    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      var approvals = List.of(
          executor.submit(() -> approveAfter(start, ready, applied.id())),
          executor.submit(() -> approveAfter(start, ready, applied.id())));
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();

      int successes = 0;
      int rejected = 0;
      for (var approval : approvals) {
        try {
          approval.get(10, TimeUnit.SECONDS);
          successes++;
        } catch (ExecutionException exception) {
          assertThat(exception.getCause()).isInstanceOf(BizException.class);
          rejected++;
        }
      }

      assertThat(successes).isEqualTo(1);
      assertThat(rejected).isEqualTo(1);
      assertThat(products.findById(product.id).orElseThrow().stock).isEqualTo(1);
      assertThat(users.findById(user.id).orElseThrow().points).isZero();
      assertThat(pointRecords.findByUserIdAndReason(user.id,
          "退款订单 " + created.orderNo() + " 积分回滚")).hasSize(1);
    } finally {
      executor.shutdownNow();
    }
  }

  private Object approveAfter(CountDownLatch start, CountDownLatch ready, Long requestId) throws Exception {
    ready.countDown();
    assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
    return afterSaleService.approve(requestId, new AfterSaleHandleRequest("concurrent approval"));
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

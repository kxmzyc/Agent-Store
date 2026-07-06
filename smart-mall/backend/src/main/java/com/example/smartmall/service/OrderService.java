package com.example.smartmall.service;

import com.example.smartmall.api.BizException;
import com.example.smartmall.api.ApiSupport.*;
import com.example.smartmall.domain.*;
import com.example.smartmall.repo.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.slf4j.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
  private static final Logger log = LoggerFactory.getLogger(OrderService.class);
  private final CartRepository carts;
  private final ProductRepository products;
  private final OrderRepository orders;
  private final OrderItemRepository orderItems;
  private final CouponService couponService;
  private final PointService pointService;

  public OrderService(CartRepository carts, ProductRepository products, OrderRepository orders, OrderItemRepository orderItems,
                      CouponService couponService, PointService pointService) {
    this.carts = carts;
    this.products = products;
    this.orders = orders;
    this.orderItems = orderItems;
    this.couponService = couponService;
    this.pointService = pointService;
  }

  @Transactional
  public CreateOrderResponse create(Long userId, CreateOrderRequest request) {
    List<CartItem> selected = carts.findByUserIdAndIdIn(userId, request.cartItemIds());
    if (selected.size() != request.cartItemIds().size()) {
      throw BizException.badRequest("购物车商品不存在");
    }

    Order order = new Order();
    order.userId = userId;
    order.orderNo = "SM" + DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").format(LocalDateTime.now())
        + String.format("%04d", new Random().nextInt(10000));
    order.status = "PENDING_PAYMENT";
    order.shippingAddress = request.shippingAddress();
    order.createdAt = LocalDateTime.now();
    order.totalAmount = BigDecimal.ZERO;
    order.discountAmount = BigDecimal.ZERO;
    order.pointsUsed = 0;
    orders.save(order);

    BigDecimal total = BigDecimal.ZERO;
    for (CartItem cart : selected) {
      Product product = products.findById(cart.productId).orElseThrow(() -> BizException.badRequest("商品不存在"));
      if (product.status == null || product.status != 1) {
        throw BizException.badRequest("商品「" + product.name + "」已下架");
      }
      int affected = products.deductStock(product.id, cart.quantity, product.version);
      if (affected == 0) {
        log.info("create order failed, stock not enough, userId={}, productId={}", userId, product.id);
        throw BizException.badRequest("商品「" + product.name + "」库存不足");
      }

      OrderItem item = new OrderItem();
      item.orderId = order.id;
      item.productId = product.id;
      item.productNameSnapshot = product.name;
      item.priceSnapshot = product.price;
      item.quantity = cart.quantity;
      orderItems.save(item);
      total = total.add(product.price.multiply(BigDecimal.valueOf(cart.quantity)));
    }

    OrderDiscount discount = applyDiscounts(userId, order.id, total, request.userCouponId(), request.usePoints());
    order.totalAmount = discount.payableAmount();
    order.discountAmount = discount.discountAmount();
    order.pointsUsed = discount.pointsUsed();
    orders.save(order);
    carts.deleteByUserIdAndIdIn(userId, request.cartItemIds());
    log.info("create order success, userId={}, orderNo={}, total={}, discount={}", userId, order.orderNo, order.totalAmount, order.discountAmount);
    return new CreateOrderResponse(order.id, order.orderNo, order.totalAmount, order.discountAmount, order.pointsUsed);
  }

  @Transactional
  public CreateOrderResponse createDirect(Long userId, Long productId, int quantity, String shippingAddress,
                                          Long userCouponId, Boolean usePoints) {
    Product product = products.findById(productId).orElseThrow(() -> BizException.badRequest("商品不存在"));
    if (product.status == null || product.status != 1) {
      throw BizException.badRequest("商品「" + product.name + "」已下架");
    }
    int affected = products.deductStock(product.id, quantity, product.version);
    if (affected == 0) {
      log.info("direct order failed, stock not enough, userId={}, productId={}", userId, product.id);
      throw BizException.badRequest("商品「" + product.name + "」库存不足");
    }

    Order order = new Order();
    order.userId = userId;
    order.orderNo = "SM" + DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").format(LocalDateTime.now())
        + String.format("%04d", new Random().nextInt(10000));
    order.status = "PENDING_PAYMENT";
    order.shippingAddress = shippingAddress;
    order.createdAt = LocalDateTime.now();
    BigDecimal total = product.price.multiply(BigDecimal.valueOf(quantity));
    order.totalAmount = BigDecimal.ZERO;
    order.discountAmount = BigDecimal.ZERO;
    order.pointsUsed = 0;
    orders.save(order);

    OrderItem item = new OrderItem();
    item.orderId = order.id;
    item.productId = product.id;
    item.productNameSnapshot = product.name;
    item.priceSnapshot = product.price;
    item.quantity = quantity;
    orderItems.save(item);
    OrderDiscount discount = applyDiscounts(userId, order.id, total, userCouponId, usePoints);
    order.totalAmount = discount.payableAmount();
    order.discountAmount = discount.discountAmount();
    order.pointsUsed = discount.pointsUsed();
    orders.save(order);
    log.info("direct order success, userId={}, orderNo={}, productId={}, total={}, discount={}",
        userId, order.orderNo, productId, order.totalAmount, order.discountAmount);
    return new CreateOrderResponse(order.id, order.orderNo, order.totalAmount, order.discountAmount, order.pointsUsed);
  }

  public OrderResponse toResponse(Order order) {
    List<OrderItemResponse> items = orderItems.findByOrderId(order.id).stream().map(OrderItemResponse::from).toList();
    return new OrderResponse(order.id, order.orderNo, order.totalAmount, order.status,
        order.shippingAddress, order.createdAt, order.paidAt, items,
        order.discountAmount == null ? BigDecimal.ZERO : order.discountAmount,
        order.pointsUsed == null ? 0 : order.pointsUsed);
  }

  @Transactional
  public OrderResponse pay(Long userId, Long orderId) {
    Order order = getOwnOrder(userId, orderId);
    requireStatus(order, "PENDING_PAYMENT");
    order.status = "PAID";
    order.paidAt = LocalDateTime.now();
    log.info("order paid, userId={}, orderId={}", userId, orderId);
    return toResponse(orders.save(order));
  }

  @Transactional
  public OrderResponse ship(Long orderId) {
    Order order = orders.findById(orderId).orElseThrow(() -> BizException.notFound("订单不存在"));
    requireStatus(order, "PAID");
    order.status = "SHIPPED";
    log.info("order shipped, orderId={}", orderId);
    return toResponse(orders.save(order));
  }

  @Transactional
  public OrderResponse confirm(Long userId, Long orderId) {
    Order order = getOwnOrder(userId, orderId);
    requireStatus(order, "SHIPPED");
    order.status = "COMPLETED";
    pointService.awardForCompletedOrder(userId, order.totalAmount, order.orderNo);
    log.info("order completed, userId={}, orderId={}", userId, orderId);
    return toResponse(orders.save(order));
  }

  @Transactional
  public OrderResponse cancel(Long userId, Long orderId) {
    Order order = getOwnOrder(userId, orderId);
    requireStatus(order, "PENDING_PAYMENT");
    order.status = "CANCELLED";
    for (OrderItem item : orderItems.findByOrderId(order.id)) {
      products.restoreStock(item.productId, item.quantity);
    }
    log.info("order cancelled and stock restored, userId={}, orderId={}", userId, orderId);
    return toResponse(orders.save(order));
  }

  private Order getOwnOrder(Long userId, Long orderId) {
    return orders.findByIdAndUserId(orderId, userId).orElseThrow(() -> BizException.notFound("订单不存在"));
  }

  private void requireStatus(Order order, String expected) {
    if (!expected.equals(order.status)) {
      throw BizException.badRequest("订单状态不允许该操作");
    }
  }

  private OrderDiscount applyDiscounts(Long userId, Long orderId, BigDecimal total, Long userCouponId, Boolean usePoints) {
    var couponResult = couponService.useCoupon(userId, userCouponId, total, orderId);
    BigDecimal afterCoupon = payable(total.subtract(couponResult.discountAmount()));
    var pointResult = pointService.usePoints(userId, afterCoupon, Boolean.TRUE.equals(usePoints));
    BigDecimal discount = couponResult.discountAmount().add(pointResult.discountAmount());
    return new OrderDiscount(payable(total.subtract(discount)), discount, pointResult.pointsUsed());
  }

  private BigDecimal payable(BigDecimal amount) {
    BigDecimal min = new BigDecimal("0.01");
    return amount.compareTo(min) < 0 ? min : amount;
  }

  private record OrderDiscount(BigDecimal payableAmount, BigDecimal discountAmount, int pointsUsed) {}
}

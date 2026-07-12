package com.example.smartmall.service;

import com.example.smartmall.api.BizException;
import com.example.smartmall.api.ApiSupport.*;
import com.example.smartmall.domain.*;
import com.example.smartmall.repo.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OrderService {
  private static final Logger log = LoggerFactory.getLogger(OrderService.class);
  private static final int MAX_STOCK_RETRY_ATTEMPTS = 200;
  private static final long STOCK_RETRY_DEADLINE_NANOS = TimeUnit.SECONDS.toNanos(5);
  private final CartRepository carts;
  private final ProductRepository products;
  private final OrderRepository orders;
  private final OrderItemRepository orderItems;
  private final CouponService couponService;
  private final PointService pointService;
  private final TransactionOperations orderAttemptTransaction;

  @Autowired
  public OrderService(CartRepository carts, ProductRepository products, OrderRepository orders, OrderItemRepository orderItems,
                      CouponService couponService, PointService pointService, PlatformTransactionManager transactionManager) {
    this(carts, products, orders, orderItems, couponService, pointService, orderAttemptTransaction(transactionManager));
  }

  OrderService(CartRepository carts, ProductRepository products, OrderRepository orders, OrderItemRepository orderItems,
               CouponService couponService, PointService pointService, TransactionOperations orderAttemptTransaction) {
    this.carts = carts;
    this.products = products;
    this.orders = orders;
    this.orderItems = orderItems;
    this.couponService = couponService;
    this.pointService = pointService;
    this.orderAttemptTransaction = orderAttemptTransaction;
  }

  private static TransactionOperations orderAttemptTransaction(PlatformTransactionManager transactionManager) {
    TransactionTemplate template = new TransactionTemplate(transactionManager);
    template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    return template;
  }

  public CreateOrderResponse create(Long userId, CreateOrderRequest request) {
    return createWithStockRetry(() -> orderAttemptTransaction.execute(status -> createAttempt(userId, request)));
  }

  private CreateOrderResponse createAttempt(Long userId, CreateOrderRequest request) {
    List<CartItem> selected = carts.findByUserIdAndIdIn(userId, request.cartItemIds());
    if (selected.size() != request.cartItemIds().size()) {
      throw BizException.badRequest("购物车商品不存在");
    }

    List<OrderLineSnapshot> snapshots = new ArrayList<>();
    BigDecimal total = BigDecimal.ZERO;
    for (CartItem cart : selected) {
      Product product = products.findById(cart.productId).orElseThrow(() -> BizException.badRequest("商品不存在"));
      validatePurchasable(product);
      deductOrConflict(product, cart.quantity);
      snapshots.add(new OrderLineSnapshot(product.id, product.name, product.price, cart.quantity));
      total = total.add(product.price.multiply(BigDecimal.valueOf(cart.quantity)));
    }

    Order order = new Order();
    order.userId = userId;
    order.orderNo = nextOrderNo();
    order.status = "PENDING_PAYMENT";
    order.shippingAddress = request.shippingAddress();
    order.createdAt = LocalDateTime.now();
    order.totalAmount = BigDecimal.ZERO;
    order.discountAmount = BigDecimal.ZERO;
    order.pointsUsed = 0;
    orders.save(order);

    for (OrderLineSnapshot snapshot : snapshots) {
      OrderItem item = new OrderItem();
      item.orderId = order.id;
      item.productId = snapshot.productId();
      item.productNameSnapshot = snapshot.productName();
      item.priceSnapshot = snapshot.price();
      item.quantity = snapshot.quantity();
      orderItems.save(item);
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

  public CreateOrderResponse createDirect(Long userId, Long productId, int quantity, String shippingAddress,
                                          Long userCouponId, Boolean usePoints) {
    return createWithStockRetry(() -> orderAttemptTransaction.execute(status ->
        createDirectAttempt(userId, productId, quantity, shippingAddress, userCouponId, usePoints)));
  }

  private CreateOrderResponse createDirectAttempt(Long userId, Long productId, int quantity, String shippingAddress,
                                                  Long userCouponId, Boolean usePoints) {
    Product product = products.findById(productId).orElseThrow(() -> BizException.badRequest("商品不存在"));
    validatePurchasable(product);
    deductOrConflict(product, quantity);

    Order order = new Order();
    order.userId = userId;
    order.orderNo = nextOrderNo();
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
    if (orders.markOwnPaid(orderId, userId, LocalDateTime.now()) == 0) {
      getOwnOrder(userId, orderId);
      throw BizException.badRequest("订单状态不允许该操作");
    }
    Order order = getOwnOrder(userId, orderId);
    log.info("order paid, userId={}, orderId={}", userId, orderId);
    return toResponse(order);
  }

  @Transactional
  public OrderResponse ship(Long orderId) {
    if (orders.transitionStatus(orderId, "PAID", "SHIPPED") == 0) {
      orders.findById(orderId).orElseThrow(() -> BizException.notFound("订单不存在"));
      throw BizException.badRequest("订单状态不允许该操作");
    }
    Order order = orders.findById(orderId).orElseThrow(() -> BizException.notFound("订单不存在"));
    log.info("order shipped, orderId={}", orderId);
    return toResponse(order);
  }

  @Transactional
  public OrderResponse confirm(Long userId, Long orderId) {
    if (orders.transitionOwnStatus(orderId, userId, "SHIPPED", "COMPLETED") == 0) {
      getOwnOrder(userId, orderId);
      throw BizException.badRequest("订单状态不允许该操作");
    }
    Order order = getOwnOrder(userId, orderId);
    pointService.awardForCompletedOrder(userId, order.totalAmount, order.orderNo);
    log.info("order completed, userId={}, orderId={}", userId, orderId);
    return toResponse(order);
  }

  @Transactional
  public OrderResponse cancel(Long userId, Long orderId) {
    if (orders.transitionOwnStatus(orderId, userId, "PENDING_PAYMENT", "CANCELLED") == 0) {
      getOwnOrder(userId, orderId);
      throw BizException.badRequest("订单状态不允许该操作");
    }
    Order order = getOwnOrder(userId, orderId);
    for (OrderItem item : orderItems.findByOrderId(order.id)) {
      products.restoreStock(item.productId, item.quantity);
    }
    log.info("order cancelled and stock restored, userId={}, orderId={}", userId, orderId);
    return toResponse(order);
  }

  private CreateOrderResponse createWithStockRetry(Supplier<CreateOrderResponse> attempt) {
    int attempts = 0;
    long deadline = System.nanoTime() + STOCK_RETRY_DEADLINE_NANOS;
    StockVersionConflictException lastConflict = null;
    while (attempts < MAX_STOCK_RETRY_ATTEMPTS && System.nanoTime() < deadline) {
      attempts++;
      try {
        CreateOrderResponse response = attempt.get();
        if (attempts > 1) {
          log.info("order stock retry success, attempts={}, orderNo={}", attempts, response.orderNo());
        }
        return response;
      } catch (StockVersionConflictException ex) {
        lastConflict = ex;
        Thread.onSpinWait();
      }
    }

    if (lastConflict != null) {
      Product current = products.findById(lastConflict.productId).orElse(null);
      if (current == null || current.status == null || current.status != 1) {
        throw BizException.badRequest("商品不存在或已下架");
      }
      if (current.stock == null || current.stock < lastConflict.quantity) {
        log.info("order stock retry stopped, stock not enough, productId={}, attempts={}",
            lastConflict.productId, attempts);
        throw BizException.badRequest("商品「" + current.name + "」库存不足");
      }
      log.warn("order stock retry exhausted while stock remains, productId={}, stock={}, version={}, attempts={}",
          current.id, current.stock, current.version, attempts);
    }
    throw BizException.conflict("库存繁忙，请重新下单");
  }

  private void validatePurchasable(Product product) {
    if (product.status == null || product.status != 1) {
      throw BizException.badRequest("商品「" + product.name + "」已下架");
    }
  }

  private void deductOrConflict(Product product, Integer quantity) {
    if (quantity == null || quantity <= 0) {
      throw BizException.badRequest("购买数量不合法");
    }
    if (product.stock == null || product.stock < quantity) {
      log.info("order failed, stock not enough, productId={}", product.id);
      throw BizException.badRequest("商品「" + product.name + "」库存不足");
    }
    int version = product.version == null ? 0 : product.version;
    int affected = products.deductStock(product.id, quantity, version);
    if (affected == 0) {
      throw new StockVersionConflictException(product.id, product.name, quantity, version);
    }
  }

  private Order getOwnOrder(Long userId, Long orderId) {
    return orders.findByIdAndUserId(orderId, userId).orElseThrow(() -> BizException.notFound("订单不存在"));
  }

  private String nextOrderNo() {
    return "SM" + UUID.randomUUID().toString().replace("-", "").substring(0, 30);
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
  private record OrderLineSnapshot(Long productId, String productName, BigDecimal price, int quantity) {}

  private static class StockVersionConflictException extends RuntimeException {
    private final Long productId;
    private final String productName;
    private final int quantity;
    private final int expectedVersion;

    private StockVersionConflictException(Long productId, String productName, int quantity, int expectedVersion) {
      super("stock version conflict");
      this.productId = productId;
      this.productName = productName;
      this.quantity = quantity;
      this.expectedVersion = expectedVersion;
    }
  }
}

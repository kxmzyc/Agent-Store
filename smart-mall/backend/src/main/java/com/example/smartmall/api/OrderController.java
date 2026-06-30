package com.example.smartmall.api;

import com.example.smartmall.domain.*;
import com.example.smartmall.repo.*;
import com.example.smartmall.security.CurrentUser;
import com.example.smartmall.service.OrderService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import static com.example.smartmall.api.ApiSupport.*;

@RestController
@RequestMapping("/api")
public class OrderController {
  private final CartRepository carts;
  private final ProductRepository products;
  private final OrderRepository orders;
  private final OrderService orderService;
  private final String internalSecret;

  public OrderController(CartRepository carts, ProductRepository products, OrderRepository orders,
                         OrderService orderService,
                         @Value("${app.internal-service-secret}") String internalSecret) {
    this.carts = carts;
    this.products = products;
    this.orders = orders;
    this.orderService = orderService;
    this.internalSecret = internalSecret;
  }

  @GetMapping("/cart")
  List<CartResponse> cart(@AuthenticationPrincipal CurrentUser user) {
    return carts.findByUserIdOrderByCreatedAtDesc(user.id()).stream().map(CartResponse::from).toList();
  }

  @PostMapping("/cart")
  ResponseEntity<CartResponse> addCart(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody CartRequest request) {
    Product product = products.findById(request.productId()).filter(p -> p.status == 1)
        .orElseThrow(() -> BizException.notFound("商品不存在"));
    if (product.stock <= 0) {
      throw BizException.badRequest("商品已售罄");
    }
    CartItem item = carts.findByUserIdAndProductId(user.id(), product.id).orElseGet(CartItem::new);
    item.userId = user.id();
    item.productId = product.id;
    int targetQty = item.id == null ? request.quantity() : item.quantity + request.quantity();
    if (targetQty > product.stock) {
      throw BizException.badRequest("超出库存数量");
    }
    item.quantity = targetQty;
    item.createdAt = item.createdAt == null ? LocalDateTime.now() : item.createdAt;
    CartItem saved = carts.save(item);
    saved.product = product;
    return ResponseEntity.status(HttpStatus.CREATED).body(CartResponse.from(saved));
  }

  @PutMapping("/cart/{id}")
  CartResponse updateCart(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                          @Valid @RequestBody CartUpdateRequest request) {
    CartItem item = carts.findByIdAndUserId(id, user.id()).orElseThrow(() -> BizException.notFound("购物车商品不存在"));
    Product product = products.findById(item.productId).orElseThrow(() -> BizException.notFound("商品不存在"));
    if (request.quantity() > product.stock) {
      throw BizException.badRequest("超出库存数量");
    }
    item.quantity = request.quantity();
    CartItem saved = carts.save(item);
    saved.product = product;
    return CartResponse.from(saved);
  }

  @DeleteMapping("/cart/{id}")
  Map<String, Object> deleteCart(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
    CartItem item = carts.findByIdAndUserId(id, user.id()).orElseThrow(() -> BizException.notFound("购物车商品不存在"));
    carts.delete(item);
    return Map.of("ok", true);
  }

  @PostMapping("/orders")
  ResponseEntity<CreateOrderResponse> createOrder(@AuthenticationPrincipal CurrentUser user,
                                                  @Valid @RequestBody CreateOrderRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(orderService.create(user.id(), request));
  }

  @PostMapping("/orders/direct")
  ResponseEntity<CreateOrderResponse> createDirectOrder(@AuthenticationPrincipal CurrentUser user,
                                                        @Valid @RequestBody DirectOrderRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(orderService.createDirect(user.id(), request.productId(), request.quantity(), request.shippingAddress()));
  }

  @GetMapping("/orders")
  List<OrderResponse> listOrders(@AuthenticationPrincipal CurrentUser user,
                                 @RequestParam(defaultValue = "all") String status) {
    List<Order> result = "all".equalsIgnoreCase(status)
        ? orders.findByUserIdOrderByCreatedAtDesc(user.id())
        : orders.findByUserIdAndStatusOrderByCreatedAtDesc(user.id(), status);
    return result.stream().map(orderService::toResponse).toList();
  }

  @GetMapping("/orders/{id}")
  OrderResponse orderDetail(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
    Order order = orders.findByIdAndUserId(id, user.id()).orElseThrow(() -> BizException.notFound("订单不存在"));
    return orderService.toResponse(order);
  }

  @PutMapping("/orders/{id}/pay")
  OrderResponse pay(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
    return orderService.pay(user.id(), id);
  }

  @PutMapping("/orders/{id}/cancel")
  OrderResponse cancel(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
    return orderService.cancel(user.id(), id);
  }

  @PutMapping("/orders/{id}/ship")
  @PreAuthorize("hasRole('ADMIN')")
  OrderResponse ship(@PathVariable Long id) {
    return orderService.ship(id);
  }

  @PutMapping("/orders/{id}/confirm")
  OrderResponse confirm(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
    return orderService.confirm(user.id(), id);
  }

  @GetMapping("/internal/orders")
  List<InternalOrderResponse> internalOrders(@RequestHeader("X-Internal-Service") String service,
                                             @RequestHeader("X-Internal-Secret") String secret,
                                             @RequestParam Long userId,
                                             @RequestParam(defaultValue = "3") int size) {
    checkInternal(service, secret);
    return orders.findRecentByUserId(userId, PageRequest.of(0, Math.min(Math.max(size, 1), 10)))
        .stream().map(InternalOrderResponse::from).toList();
  }

  @GetMapping("/internal/orders/{id}")
  InternalOrderResponse internalOrder(@RequestHeader("X-Internal-Service") String service,
                                      @RequestHeader("X-Internal-Secret") String secret,
                                      @PathVariable Long id,
                                      @RequestParam Long userId) {
    checkInternal(service, secret);
    return orders.findByIdAndUserId(id, userId).map(InternalOrderResponse::from)
        .orElseThrow(() -> BizException.notFound("订单不存在"));
  }

  private void checkInternal(String service, String secret) {
    if (!"agent".equals(service) || !internalSecret.equals(secret)) {
      throw new BizException(HttpStatus.FORBIDDEN, "内部服务认证失败");
    }
  }
}

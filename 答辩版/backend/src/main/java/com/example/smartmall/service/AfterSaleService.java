package com.example.smartmall.service;

import com.example.smartmall.api.ApiSupport.*;
import com.example.smartmall.api.BizException;
import com.example.smartmall.domain.*;
import com.example.smartmall.repo.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.slf4j.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AfterSaleService {
  private static final Logger log = LoggerFactory.getLogger(AfterSaleService.class);
  private static final Set<String> APPLICABLE_ORDER_STATUSES = Set.of("SHIPPED", "COMPLETED");
  private static final List<Integer> OPEN_REQUEST_STATUSES = List.of(0, 1, 3);

  private final AfterSaleRequestRepository afterSales;
  private final OrderRepository orders;
  private final OrderItemRepository orderItems;
  private final ProductRepository products;
  private final UserCouponRepository userCoupons;
  private final PointService pointService;

  public AfterSaleService(AfterSaleRequestRepository afterSales, OrderRepository orders,
                          OrderItemRepository orderItems, ProductRepository products,
                          UserCouponRepository userCoupons, PointService pointService) {
    this.afterSales = afterSales;
    this.orders = orders;
    this.orderItems = orderItems;
    this.products = products;
    this.userCoupons = userCoupons;
    this.pointService = pointService;
  }

  @Transactional
  public AfterSaleResponse apply(Long userId, Long orderId, AfterSaleApplyRequest request) {
    Order order = orders.findByIdAndUserId(orderId, userId)
        .orElseThrow(() -> BizException.notFound("订单不存在"));
    if (!APPLICABLE_ORDER_STATUSES.contains(order.status)) {
      throw BizException.badRequest("当前订单状态暂不支持申请售后");
    }
    if (afterSales.existsByOrderIdAndStatusIn(orderId, OPEN_REQUEST_STATUSES)) {
      throw BizException.conflict("该订单已有售后记录");
    }

    AfterSaleRequest entity = new AfterSaleRequest();
    entity.orderId = order.id;
    entity.userId = userId;
    entity.type = request.type();
    entity.reason = request.reason();
    entity.status = 0;
    entity.refundAmount = order.totalAmount;
    entity.createdAt = LocalDateTime.now();
    AfterSaleRequest saved = afterSales.save(entity);
    log.info("after-sale requested, userId={}, orderId={}, requestId={}", userId, orderId, saved.id);
    return toResponse(saved);
  }

  public List<AfterSaleResponse> myRequests(Long userId) {
    return afterSales.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toResponse).toList();
  }

  public Page<AfterSaleResponse> adminList(String status, int page, int size) {
    Pageable pageable = PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(size, 1), 50));
    Page<AfterSaleRequest> result = "all".equalsIgnoreCase(status)
        ? afterSales.findAllByOrderByCreatedAtDesc(pageable)
        : afterSales.findByStatusOrderByCreatedAtDesc(Integer.parseInt(status), pageable);
    return result.map(this::toResponse);
  }

  @Transactional
  public AfterSaleResponse approve(Long id, AfterSaleHandleRequest request) {
    AfterSaleRequest entity = afterSales.findByIdForUpdate(id)
        .orElseThrow(() -> BizException.notFound("售后申请不存在"));
    requirePending(entity);
    Order order = orders.findById(entity.orderId).orElseThrow(() -> BizException.notFound("订单不存在"));

    if (Integer.valueOf(2).equals(entity.type)) {
      for (OrderItem item : orderItems.findByOrderId(order.id)) {
        products.restoreStock(item.productId, item.quantity);
      }
    }
    for (UserCoupon coupon : userCoupons.findByUsedOrderId(order.id)) {
      coupon.status = 0;
      coupon.usedOrderId = null;
      userCoupons.save(coupon);
    }
    if ("COMPLETED".equals(order.status)) {
      pointService.reverseCompletedOrderAward(order.userId, order.totalAmount, order.orderNo);
    }

    order.status = "REFUNDED";
    orders.save(order);
    entity.status = 3;
    entity.handledAt = LocalDateTime.now();
    entity.handleRemark = normalizedRemark(request, "售后已同意，库存与优惠券已回滚");
    AfterSaleRequest saved = afterSales.save(entity);
    log.info("after-sale approved, requestId={}, orderId={}, refundAmount={}", id, order.id, entity.refundAmount);
    return toResponse(saved);
  }

  @Transactional
  public AfterSaleResponse reject(Long id, AfterSaleHandleRequest request) {
    AfterSaleRequest entity = afterSales.findByIdForUpdate(id)
        .orElseThrow(() -> BizException.notFound("售后申请不存在"));
    requirePending(entity);
    entity.status = 2;
    entity.handledAt = LocalDateTime.now();
    entity.handleRemark = normalizedRemark(request, "售后申请已拒绝");
    AfterSaleRequest saved = afterSales.save(entity);
    log.info("after-sale rejected, requestId={}, orderId={}", id, entity.orderId);
    return toResponse(saved);
  }

  public AfterSaleResponse toResponse(AfterSaleRequest entity) {
    Order order = orders.findById(entity.orderId).orElse(null);
    List<OrderItemResponse> items = orderItems.findByOrderId(entity.orderId).stream()
        .map(OrderItemResponse::from)
        .toList();
    return new AfterSaleResponse(entity.id, entity.orderId, order == null ? "" : order.orderNo, entity.userId,
        entity.type, typeLabel(entity.type), entity.reason, entity.status, statusLabel(entity.status),
        entity.refundAmount, entity.createdAt, entity.handledAt, entity.handleRemark, items);
  }

  private void requirePending(AfterSaleRequest entity) {
    if (entity.status == null || entity.status != 0) {
      throw BizException.badRequest("售后申请已处理");
    }
  }

  private String normalizedRemark(AfterSaleHandleRequest request, String fallback) {
    if (request == null || request.remark() == null || request.remark().isBlank()) return fallback;
    return request.remark().trim();
  }

  private String typeLabel(Integer type) {
    return Integer.valueOf(2).equals(type) ? "退货退款" : "仅退款";
  }

  private String statusLabel(Integer status) {
    if (Integer.valueOf(1).equals(status)) return "已同意";
    if (Integer.valueOf(2).equals(status)) return "已拒绝";
    if (Integer.valueOf(3).equals(status)) return "已完成";
    return "待审核";
  }
}

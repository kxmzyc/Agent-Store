package com.example.smartmall.api;

import com.example.smartmall.domain.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

public class ApiSupport {
  public record ErrorResponse(String msg) {}
  public record PageResponse<T>(long total, List<T> list) {
    public static <T> PageResponse<T> from(Page<T> page) {
      return new PageResponse<>(page.getTotalElements(), page.getContent());
    }
  }

  public record RegisterRequest(@NotBlank String username, @Size(min = 6) String password, String phone) {}
  public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
  public record RefreshRequest(@NotBlank String refreshToken) {}
  public record ProfileUpdateRequest(String phone, String avatarUrl) {}
  public record UserResponse(Long id, String username, String phone, String avatarUrl, String role,
                             LocalDateTime createdAt, Integer points) {
    public static UserResponse from(User u) {
      return new UserResponse(u.id, u.username, u.phone, u.avatarUrl, u.role, u.createdAt, u.points == null ? 0 : u.points);
    }
  }
  public record RegisterResponse(Long userId, String username) {}
  public record LoginResponse(String accessToken, String refreshToken, UserResponse userInfo) {}
  public record TokenResponse(String accessToken) {}

  public record CategoryResponse(Long id, String name, Long parentId, Integer sortOrder, List<CategoryResponse> children) {}
  public record ProductRequest(@NotNull Long categoryId, @NotBlank String name, String description,
                               @NotNull BigDecimal price, @NotNull Integer stock, String imageUrl, Integer status,
                               List<Long> tagIds) {}
  public record TagResponse(Long id, String name) {
    public static TagResponse from(ProductTag tag) {
      return new TagResponse(tag.id, tag.name);
    }
  }
  public record ProductResponse(Long id, Long categoryId, String name, String description, BigDecimal price,
                                Integer stock, Integer salesCount, String imageUrl, Integer status, Integer version,
                                Double avgRating, Long reviewCount, List<TagResponse> tags) {
    public static ProductResponse from(Product p) {
      return from(p, List.of());
    }
    public static ProductResponse from(Product p, List<TagResponse> tags) {
      return new ProductResponse(p.id, p.categoryId, p.name, p.description, p.price, p.stock,
          p.salesCount, p.imageUrl, p.status, p.version, 0.0, 0L, tags);
    }
  }
  public record RecommendationResponse(String source, List<ProductResponse> list) {}
  public record ReviewRequest(@Min(1) @Max(5) Integer rating, String content) {}
  public record ReviewResponse(Long id, Long productId, Long userId, String username, Integer rating,
                               String content, LocalDateTime createdAt) {
    public static ReviewResponse from(ProductReview review, String username) {
      return new ReviewResponse(review.id, review.productId, review.userId, username,
          review.rating, review.content, review.createdAt);
    }
  }
  public record FavoriteStatusResponse(boolean favorited) {}
  public record ProductBriefResponse(Long id, String name, String description, BigDecimal price,
                                     Integer stock, Integer salesCount, String imageUrl) {
    public static ProductBriefResponse from(Product p) {
      return new ProductBriefResponse(p.id, p.name, p.description, p.price, p.stock, p.salesCount, p.imageUrl);
    }
  }
  public record FavoriteResponse(Long id, LocalDateTime createdAt, ProductBriefResponse product) {
    public static FavoriteResponse from(ProductFavorite f) {
      return new FavoriteResponse(f.id, f.createdAt, ProductBriefResponse.from(f.product));
    }
  }
  public record ProductViewHistoryResponse(Long id, Integer viewCount, LocalDateTime lastViewedAt,
                                           ProductBriefResponse product) {
    public static ProductViewHistoryResponse from(ProductViewHistory h) {
      return new ProductViewHistoryResponse(h.id, h.viewCount, h.lastViewedAt, ProductBriefResponse.from(h.product));
    }
  }
  public record UserOverviewResponse(long cartItems, long favorites, long viewedProducts,
                                     long pendingPaymentOrders, long paidOrders, long shippedOrders,
                                     long completedOrders, long cancelledOrders,
                                     List<ProductViewHistoryResponse> recentViews,
                                     List<FavoriteResponse> recentFavorites) {}

  public record AddressRequest(@NotBlank String receiverName, @NotBlank String phone,
                               @NotBlank String province, @NotBlank String city,
                               @NotBlank String district, @NotBlank String detailAddress,
                               Boolean isDefault) {}
  public record AddressResponse(Long id, String receiverName, String phone, String province,
                                String city, String district, String detailAddress,
                                boolean isDefault, LocalDateTime createdAt) {
    public static AddressResponse from(ShippingAddress address) {
      return new AddressResponse(address.id, address.receiverName, address.phone, address.province,
          address.city, address.district, address.detailAddress, address.isDefault != null && address.isDefault == 1,
          address.createdAt);
    }
  }

  public record CartRequest(@NotNull Long productId, @Min(1) Integer quantity) {}
  public record CartUpdateRequest(@Min(1) Integer quantity) {}
  public record InternalCartRequest(@NotNull Long userId, @NotNull Long productId, @Min(1) Integer quantity) {}
  public record CartResponse(Long id, Long productId, String productName, BigDecimal price, String imageUrl,
                             Integer stock, Integer quantity, BigDecimal subtotal) {
    public static CartResponse from(CartItem item) {
      Product p = item.product;
      return new CartResponse(item.id, item.productId, p.name, p.price, p.imageUrl, p.stock,
          item.quantity, p.price.multiply(BigDecimal.valueOf(item.quantity)));
    }
  }

  public record CouponResponse(Long id, String name, Integer type, BigDecimal threshold, BigDecimal discount,
                               Integer totalCount, Integer remainCount, Integer validDays, boolean claimed) {
    public static CouponResponse from(Coupon c, boolean claimed) {
      return new CouponResponse(c.id, c.name, c.type, c.threshold, c.discount, c.totalCount,
          c.remainCount, c.validDays, claimed);
    }
  }
  public record UserCouponResponse(Long id, Long couponId, String name, Integer type, BigDecimal threshold,
                                   BigDecimal discount, Integer status, java.time.LocalDate expireAt) {
    public static UserCouponResponse from(UserCoupon uc) {
      return from(uc, uc.coupon);
    }
    public static UserCouponResponse from(UserCoupon uc, Coupon c) {
      return new UserCouponResponse(uc.id, uc.couponId, c == null || c.name == null ? "" : c.name,
          c == null || c.type == null ? 1 : c.type,
          c == null || c.threshold == null ? BigDecimal.ZERO : c.threshold,
          c == null || c.discount == null ? BigDecimal.ZERO : c.discount,
          uc.status, uc.expireAt);
    }
  }
  public record CouponCalculateRequest(@NotNull Long userCouponId, @NotNull BigDecimal orderAmount) {}
  public record CouponCalculateResponse(Long userCouponId, BigDecimal orderAmount, BigDecimal discountAmount,
                                        BigDecimal payableAmount) {}
  public record PointRecordResponse(Integer delta, String reason, LocalDateTime createdAt) {
    public static PointRecordResponse from(PointRecord record) {
      return new PointRecordResponse(record.delta, record.reason, record.createdAt);
    }
  }
  public record PointsResponse(Integer points, List<PointRecordResponse> records) {}
  public record FeedbackRequest(@NotNull Integer type, @NotBlank String content) {}
  public record FeedbackReplyRequest(@NotBlank String reply) {}
  public record FeedbackResponse(Long id, Long userId, String username, Integer type, String content,
                                 Integer status, String reply, LocalDateTime createdAt) {
    public static FeedbackResponse from(UserFeedback feedback, String username) {
      return new FeedbackResponse(feedback.id, feedback.userId, username, feedback.type, feedback.content,
          feedback.status, feedback.reply, feedback.createdAt);
    }
  }

  public record AfterSaleApplyRequest(@NotNull @Min(1) @Max(2) Integer type, @NotBlank @Size(max = 200) String reason) {}
  public record AfterSaleHandleRequest(@Size(max = 200) String remark) {}
  public record AfterSaleResponse(Long id, Long orderId, String orderNo, Long userId, Integer type,
                                  String typeLabel, String reason, Integer status, String statusLabel,
                                  BigDecimal refundAmount, LocalDateTime createdAt, LocalDateTime handledAt,
                                  String handleRemark, List<OrderItemResponse> items) {}

  public record CreateOrderRequest(@NotEmpty List<Long> cartItemIds, @NotBlank String shippingAddress,
                                   Long userCouponId, Boolean usePoints) {}
  public record DirectOrderRequest(@NotNull Long productId, @Min(1) Integer quantity, @NotBlank String shippingAddress,
                                   Long userCouponId, Boolean usePoints) {}
  public record CreateOrderResponse(Long orderId, String orderNo, BigDecimal totalAmount,
                                    BigDecimal discountAmount, Integer pointsUsed) {}
  public record OrderItemResponse(Long productId, String productName, BigDecimal price, Integer quantity) {
    public static OrderItemResponse from(OrderItem i) {
      return new OrderItemResponse(i.productId, i.productNameSnapshot, i.priceSnapshot, i.quantity);
    }
  }
  public record OrderResponse(Long id, String orderNo, BigDecimal totalAmount, String status,
                              String shippingAddress, LocalDateTime createdAt, LocalDateTime paidAt,
                              List<OrderItemResponse> items, BigDecimal discountAmount, Integer pointsUsed) {}
  public record AdminOrderResponse(Long id, String orderNo, BigDecimal totalAmount, String status,
                                   String shippingAddress, LocalDateTime createdAt, LocalDateTime paidAt,
                                   List<OrderItemResponse> items, BigDecimal discountAmount, Integer pointsUsed,
                                   Long userId, String username, String phone) {
    public static AdminOrderResponse from(OrderResponse order, Long userId, String username, String phone) {
      return new AdminOrderResponse(order.id(), order.orderNo(), order.totalAmount(), order.status(),
          order.shippingAddress(), order.createdAt(), order.paidAt(), order.items(), order.discountAmount(),
          order.pointsUsed(), userId, username, phone);
    }
  }
  public record RebuyResponse(int addedCount, List<CartResponse> cartItems) {}
  public record InternalOrderResponse(Long id, String orderNo, String status, BigDecimal totalAmount, LocalDateTime createdAt) {
    public static InternalOrderResponse from(Order o) {
      return new InternalOrderResponse(o.id, o.orderNo, o.status, o.totalAmount, o.createdAt);
    }
  }
  public record AdminMetricResponse(long users, long products, long soldOutProducts, long orders,
                                    long pendingPaymentOrders, long paidOrders, long shippedOrders,
                                    long completedOrders, BigDecimal effectiveRevenue) {}
  public record AdminProductBrief(Long id, String name, BigDecimal price, Integer stock,
                                  Integer salesCount, Integer status, String imageUrl, Long categoryId, String category) {
    public static AdminProductBrief from(Product p) {
      return new AdminProductBrief(p.id, p.name, p.price, p.stock, p.salesCount, p.status, p.imageUrl, p.categoryId, null);
    }
    public static AdminProductBrief from(Product p, String category) {
      return new AdminProductBrief(p.id, p.name, p.price, p.stock, p.salesCount, p.status, p.imageUrl, p.categoryId, category);
    }
  }
  public record AdminOrderBrief(Long id, String orderNo, String status, BigDecimal totalAmount, LocalDateTime createdAt) {
    public static AdminOrderBrief from(Order o) {
      return new AdminOrderBrief(o.id, o.orderNo, o.status, o.totalAmount, o.createdAt);
    }
  }
  public record DailyOrderCount(String date, long count) {}
  public record CategorySales(String category, long sales) {}
  public record AdminDashboardResponse(AdminMetricResponse metrics, List<AdminProductBrief> lowStockProducts,
                                       List<AdminProductBrief> topProducts, List<AdminOrderBrief> recentOrders,
                                       long todayOrders, BigDecimal monthRevenue, long productCount, long userCount,
                                       List<DailyOrderCount> last7DaysOrders, List<CategorySales> categoryTopSales) {}
  public record BannerSlotRequest(@NotNull Long productId, Integer sortOrder, Boolean active) {}
  public record BannerSlotResponse(Long id, ProductResponse product, Integer sortOrder, boolean active,
                                   LocalDateTime createdAt) {
    public static BannerSlotResponse from(BannerSlot slot) {
      return new BannerSlotResponse(slot.id, slot.product == null ? null : ProductResponse.from(slot.product),
          slot.sortOrder, slot.isActive != null && slot.isActive == 1, slot.createdAt);
    }
  }
  public record TagRequest(@NotBlank String name) {}
  public record HotKeywordResponse(String keyword, long count, boolean blocked, LocalDateTime lastSearchedAt) {}
  public record KeywordBlockRequest(Boolean blocked) {}
  public record AdminOperationLogResponse(Long id, Long adminId, String action, String targetType, Long targetId,
                                          String detail, LocalDateTime createdAt) {
    public static AdminOperationLogResponse from(AdminOperationLog log) {
      return new AdminOperationLogResponse(log.id, log.adminId, log.action, log.targetType,
          log.targetId, log.detail, log.createdAt);
    }
  }
}

@RestControllerAdvice
class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(BizException.class)
  ResponseEntity<ApiSupport.ErrorResponse> biz(BizException ex) {
    return ResponseEntity.status(ex.status).body(new ApiSupport.ErrorResponse(ex.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiSupport.ErrorResponse> validation(MethodArgumentNotValidException ex) {
    return ResponseEntity.badRequest().body(new ApiSupport.ErrorResponse("请求参数不合法"));
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ApiSupport.ErrorResponse> denied() {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiSupport.ErrorResponse("无权限访问"));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiSupport.ErrorResponse> other(Exception ex) {
    log.error("Unhandled API exception", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiSupport.ErrorResponse("系统繁忙，请稍后再试"));
  }
}

@RestController
class HealthController {
  @GetMapping("/health")
  java.util.Map<String, Object> health() {
    return java.util.Map.of("status", "ok");
  }
}

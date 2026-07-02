package com.example.smartmall.api;

import com.example.smartmall.domain.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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
  public record UserResponse(Long id, String username, String phone, String avatarUrl, String role, LocalDateTime createdAt) {
    public static UserResponse from(User u) {
      return new UserResponse(u.id, u.username, u.phone, u.avatarUrl, u.role, u.createdAt);
    }
  }
  public record RegisterResponse(Long userId, String username) {}
  public record LoginResponse(String accessToken, String refreshToken, UserResponse userInfo) {}
  public record TokenResponse(String accessToken) {}

  public record CategoryResponse(Long id, String name, Long parentId, Integer sortOrder, List<CategoryResponse> children) {}
  public record ProductRequest(@NotNull Long categoryId, @NotBlank String name, String description,
                               @NotNull BigDecimal price, @NotNull Integer stock, String imageUrl, Integer status) {}
  public record ProductResponse(Long id, Long categoryId, String name, String description, BigDecimal price,
                                Integer stock, Integer salesCount, String imageUrl, Integer status, Integer version) {
    public static ProductResponse from(Product p) {
      return new ProductResponse(p.id, p.categoryId, p.name, p.description, p.price, p.stock,
          p.salesCount, p.imageUrl, p.status, p.version);
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

  public record CreateOrderRequest(@NotEmpty List<Long> cartItemIds, @NotBlank String shippingAddress) {}
  public record DirectOrderRequest(@NotNull Long productId, @Min(1) Integer quantity, @NotBlank String shippingAddress) {}
  public record CreateOrderResponse(Long orderId, String orderNo, BigDecimal totalAmount) {}
  public record OrderItemResponse(Long productId, String productName, BigDecimal price, Integer quantity) {
    public static OrderItemResponse from(OrderItem i) {
      return new OrderItemResponse(i.productId, i.productNameSnapshot, i.priceSnapshot, i.quantity);
    }
  }
  public record OrderResponse(Long id, String orderNo, BigDecimal totalAmount, String status,
                              String shippingAddress, LocalDateTime createdAt, LocalDateTime paidAt,
                              List<OrderItemResponse> items) {}
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
                                  Integer salesCount, Integer status, String imageUrl) {
    public static AdminProductBrief from(Product p) {
      return new AdminProductBrief(p.id, p.name, p.price, p.stock, p.salesCount, p.status, p.imageUrl);
    }
  }
  public record AdminOrderBrief(Long id, String orderNo, String status, BigDecimal totalAmount, LocalDateTime createdAt) {
    public static AdminOrderBrief from(Order o) {
      return new AdminOrderBrief(o.id, o.orderNo, o.status, o.totalAmount, o.createdAt);
    }
  }
  public record AdminDashboardResponse(AdminMetricResponse metrics, List<AdminProductBrief> lowStockProducts,
                                       List<AdminProductBrief> topProducts, List<AdminOrderBrief> recentOrders) {}
}

@RestControllerAdvice
class GlobalExceptionHandler {
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

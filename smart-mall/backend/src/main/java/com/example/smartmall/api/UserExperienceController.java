package com.example.smartmall.api;

import com.example.smartmall.repo.*;
import com.example.smartmall.security.CurrentUser;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import static com.example.smartmall.api.ApiSupport.*;

@RestController
@RequestMapping("/api/user")
public class UserExperienceController {
  private final CartRepository carts;
  private final OrderRepository orders;
  private final ProductFavoriteRepository favorites;
  private final ProductViewHistoryRepository viewHistory;

  public UserExperienceController(CartRepository carts, OrderRepository orders,
                                  ProductFavoriteRepository favorites,
                                  ProductViewHistoryRepository viewHistory) {
    this.carts = carts;
    this.orders = orders;
    this.favorites = favorites;
    this.viewHistory = viewHistory;
  }

  @GetMapping("/overview")
  UserOverviewResponse overview(@AuthenticationPrincipal CurrentUser user) {
    List<ProductViewHistoryResponse> recentViews = viewHistory
        .findByUserIdOrderByLastViewedAtDesc(user.id(), PageRequest.of(0, 6))
        .stream()
        .filter(h -> h.product != null && h.product.status == 1)
        .map(ProductViewHistoryResponse::from)
        .toList();
    List<FavoriteResponse> recentFavorites = favorites
        .findByUserIdOrderByCreatedAtDesc(user.id(), PageRequest.of(0, 6))
        .stream()
        .filter(f -> f.product != null && f.product.status == 1)
        .map(FavoriteResponse::from)
        .toList();

    return new UserOverviewResponse(
        carts.countByUserId(user.id()),
        favorites.countByUserId(user.id()),
        viewHistory.countByUserId(user.id()),
        orders.countByUserIdAndStatus(user.id(), "PENDING_PAYMENT"),
        orders.countByUserIdAndStatus(user.id(), "PAID"),
        orders.countByUserIdAndStatus(user.id(), "SHIPPED"),
        orders.countByUserIdAndStatus(user.id(), "COMPLETED"),
        orders.countByUserIdAndStatus(user.id(), "CANCELLED"),
        recentViews,
        recentFavorites);
  }
}

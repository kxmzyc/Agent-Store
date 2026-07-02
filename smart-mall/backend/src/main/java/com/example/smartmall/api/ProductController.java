package com.example.smartmall.api;

import com.example.smartmall.domain.*;
import com.example.smartmall.repo.*;
import com.example.smartmall.security.CurrentUser;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import static com.example.smartmall.api.ApiSupport.*;

@RestController
@RequestMapping("/api")
public class ProductController {
  private final CategoryRepository categories;
  private final ProductRepository products;
  private final ProductFavoriteRepository favorites;
  private final ProductViewHistoryRepository viewHistory;

  public ProductController(CategoryRepository categories, ProductRepository products,
                           ProductFavoriteRepository favorites,
                           ProductViewHistoryRepository viewHistory) {
    this.categories = categories;
    this.products = products;
    this.favorites = favorites;
    this.viewHistory = viewHistory;
  }

  @GetMapping("/categories")
  List<CategoryResponse> categories() {
    List<Category> all = categories.findAllByOrderBySortOrderAscIdAsc();
    Map<Long, List<Category>> byParent = all.stream()
        .filter(c -> c.parentId != null)
        .collect(Collectors.groupingBy(c -> c.parentId));
    return all.stream()
        .filter(c -> c.parentId == null)
        .map(c -> toCategory(c, byParent))
        .toList();
  }

  @GetMapping("/products")
  PageResponse<ProductResponse> list(@RequestParam(required = false) Long categoryId,
                                     @RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "20") int size,
                                     @RequestParam(defaultValue = "sales_desc") String sort) {
    Pageable pageable = PageRequest.of(Math.max(page, 1) - 1, Math.min(Math.max(size, 1), 100), toSort(sort));
    Page<Product> result = categoryId == null
        ? products.findByStatus(1, pageable)
        : products.findByStatusAndCategoryId(1, categoryId, pageable);
    return new PageResponse<>(result.getTotalElements(), result.getContent().stream().map(ProductResponse::from).toList());
  }

  @GetMapping("/products/admin")
  @PreAuthorize("hasRole('ADMIN')")
  PageResponse<ProductResponse> adminList(@RequestParam(required = false) Long categoryId,
                                          @RequestParam(defaultValue = "") String keyword,
                                          @RequestParam(defaultValue = "all") String status,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "20") int size) {
    Pageable pageable = PageRequest.of(Math.max(page, 1) - 1, Math.min(Math.max(size, 1), 100),
        Sort.by(Sort.Direction.DESC, "id"));
    Integer statusValue = parseStatus(status);
    Page<Product> result = products.adminSearch(keyword.trim(), categoryId, statusValue, pageable);
    return new PageResponse<>(result.getTotalElements(), result.getContent().stream().map(ProductResponse::from).toList());
  }

  @GetMapping("/products/search")
  PageResponse<ProductResponse> search(@RequestParam(defaultValue = "") String keyword,
                                       @RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "20") int size) {
    Pageable pageable = PageRequest.of(Math.max(page, 1) - 1, Math.min(Math.max(size, 1), 100),
        Sort.by(Sort.Direction.DESC, "salesCount").and(Sort.by(Sort.Direction.DESC, "id")));
    Page<Product> result = products.search(keyword, pageable);
    return new PageResponse<>(result.getTotalElements(), result.getContent().stream().map(ProductResponse::from).toList());
  }

  @GetMapping("/products/{id}")
  ProductResponse detail(@PathVariable Long id, @AuthenticationPrincipal CurrentUser user) {
    Product product = products.findById(id).filter(p -> p.status == 1)
        .orElseThrow(() -> BizException.notFound("商品不存在"));
    if (user != null) {
      recordView(user.id(), product.id);
    }
    return ProductResponse.from(product);
  }

  @GetMapping("/user/favorites")
  List<FavoriteResponse> favorites(@AuthenticationPrincipal CurrentUser user,
                                   @RequestParam(defaultValue = "20") int size) {
    return favorites.findByUserIdOrderByCreatedAtDesc(user.id(), PageRequest.of(0, Math.min(Math.max(size, 1), 50)))
        .stream().filter(f -> f.product != null && f.product.status == 1).map(FavoriteResponse::from).toList();
  }

  @GetMapping("/user/favorites/{productId}")
  FavoriteStatusResponse favoriteStatus(@AuthenticationPrincipal CurrentUser user, @PathVariable Long productId) {
    return new FavoriteStatusResponse(favorites.existsByUserIdAndProductId(user.id(), productId));
  }

  @PostMapping("/user/favorites/{productId}")
  @Transactional
  FavoriteStatusResponse addFavorite(@AuthenticationPrincipal CurrentUser user, @PathVariable Long productId) {
    products.findById(productId).filter(p -> p.status == 1)
        .orElseThrow(() -> BizException.notFound("商品不存在"));
    favorites.findByUserIdAndProductId(user.id(), productId).orElseGet(() -> {
      ProductFavorite favorite = new ProductFavorite();
      favorite.userId = user.id();
      favorite.productId = productId;
      favorite.createdAt = LocalDateTime.now();
      return favorites.save(favorite);
    });
    return new FavoriteStatusResponse(true);
  }

  @DeleteMapping("/user/favorites/{productId}")
  @Transactional
  FavoriteStatusResponse removeFavorite(@AuthenticationPrincipal CurrentUser user, @PathVariable Long productId) {
    favorites.deleteByUserIdAndProductId(user.id(), productId);
    return new FavoriteStatusResponse(false);
  }

  @GetMapping("/user/view-history")
  List<ProductViewHistoryResponse> viewHistory(@AuthenticationPrincipal CurrentUser user,
                                               @RequestParam(defaultValue = "20") int size) {
    return viewHistory.findByUserIdOrderByLastViewedAtDesc(user.id(), PageRequest.of(0, Math.min(Math.max(size, 1), 50)))
        .stream().filter(h -> h.product != null && h.product.status == 1).map(ProductViewHistoryResponse::from).toList();
  }

  @DeleteMapping("/user/view-history")
  @Transactional
  Map<String, Object> clearViewHistory(@AuthenticationPrincipal CurrentUser user) {
    viewHistory.deleteByUserId(user.id());
    return Map.of("ok", true);
  }

  @PostMapping("/products")
  @PreAuthorize("hasRole('ADMIN')")
  ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
    Product product = new Product();
    apply(product, request);
    product.salesCount = 0;
    product.version = 0;
    product.createdAt = LocalDateTime.now();
    return ResponseEntity.status(HttpStatus.CREATED).body(ProductResponse.from(products.save(product)));
  }

  @PutMapping("/products/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
    Product product = products.findById(id).orElseThrow(() -> BizException.notFound("商品不存在"));
    apply(product, request);
    return ProductResponse.from(products.save(product));
  }

  @DeleteMapping("/products/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  Map<String, Object> delete(@PathVariable Long id) {
    Product product = products.findById(id).orElseThrow(() -> BizException.notFound("商品不存在"));
    product.status = 0;
    products.save(product);
    return Map.of("ok", true);
  }

  private CategoryResponse toCategory(Category category, Map<Long, List<Category>> byParent) {
    List<CategoryResponse> children = byParent.getOrDefault(category.id, List.of()).stream()
        .map(c -> toCategory(c, byParent))
        .toList();
    return new CategoryResponse(category.id, category.name, category.parentId, category.sortOrder, children);
  }

  private Sort toSort(String sort) {
    return switch (sort) {
      case "price_asc" -> Sort.by(Sort.Direction.ASC, "price");
      case "price_desc" -> Sort.by(Sort.Direction.DESC, "price");
      case "new_desc" -> Sort.by(Sort.Direction.DESC, "createdAt");
      default -> Sort.by(Sort.Direction.DESC, "salesCount").and(Sort.by(Sort.Direction.DESC, "id"));
    };
  }

  private Integer parseStatus(String status) {
    if ("0".equals(status) || "1".equals(status)) {
      return Integer.valueOf(status);
    }
    return null;
  }

  private void apply(Product product, ProductRequest request) {
    categories.findById(request.categoryId()).orElseThrow(() -> BizException.badRequest("分类不存在"));
    product.categoryId = request.categoryId();
    product.name = request.name();
    product.description = request.description();
    product.price = request.price();
    product.stock = request.stock();
    product.imageUrl = request.imageUrl();
    product.status = request.status() == null ? 1 : request.status();
  }

  private void recordView(Long userId, Long productId) {
    ProductViewHistory history = viewHistory.findByUserIdAndProductId(userId, productId).orElseGet(ProductViewHistory::new);
    LocalDateTime now = LocalDateTime.now();
    history.userId = userId;
    history.productId = productId;
    history.viewCount = history.viewCount == null ? 1 : history.viewCount + 1;
    history.createdAt = history.createdAt == null ? now : history.createdAt;
    history.lastViewedAt = now;
    viewHistory.save(history);
  }
}

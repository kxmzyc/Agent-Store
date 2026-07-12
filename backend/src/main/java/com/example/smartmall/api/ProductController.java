package com.example.smartmall.api;

import com.example.smartmall.domain.*;
import com.example.smartmall.repo.*;
import com.example.smartmall.audit.AdminOperation;
import com.example.smartmall.security.CurrentUser;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import static com.example.smartmall.api.ApiSupport.*;

@RestController
@RequestMapping("/api")
public class ProductController {
  private final CategoryRepository categories;
  private final ProductRepository products;
  private final ProductFavoriteRepository favorites;
  private final ProductViewHistoryRepository viewHistory;
  private final ProductViewLogRepository viewLogs;
  private final ProductReviewRepository reviews;
  private final OrderItemRepository orderItems;
  private final UserRepository users;
  private final UserPreferenceRepository preferences;
  private final SearchKeywordLogRepository keywordLogs;
  private final ProductTagRepository tags;
  private final ProductTagRelationRepository tagRelations;
  private final Path uploadDirectory;

  public ProductController(CategoryRepository categories, ProductRepository products,
                           ProductFavoriteRepository favorites,
                           ProductViewHistoryRepository viewHistory,
                           ProductViewLogRepository viewLogs,
                           ProductReviewRepository reviews,
                           OrderItemRepository orderItems,
                           UserRepository users,
                           UserPreferenceRepository preferences,
                           SearchKeywordLogRepository keywordLogs,
                           ProductTagRepository tags,
                           ProductTagRelationRepository tagRelations,
                           @Value("${app.upload-dir:uploads}") String uploadDir) {
    this.categories = categories;
    this.products = products;
    this.favorites = favorites;
    this.viewHistory = viewHistory;
    this.viewLogs = viewLogs;
    this.reviews = reviews;
    this.orderItems = orderItems;
    this.users = users;
    this.preferences = preferences;
    this.keywordLogs = keywordLogs;
    this.tags = tags;
    this.tagRelations = tagRelations;
    this.uploadDirectory = Path.of(uploadDir).toAbsolutePath().normalize();
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
        : products.findByStatusAndCategoryIdIn(1, categoryIdsWithChildren(categoryId), pageable);
    return new PageResponse<>(result.getTotalElements(), toProductResponses(result.getContent()));
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
    return new PageResponse<>(result.getTotalElements(), toProductResponses(result.getContent()));
  }

  @GetMapping("/products/search")
  PageResponse<ProductResponse> search(@RequestParam(defaultValue = "") String keyword,
                                       @RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "20") int size,
                                       @AuthenticationPrincipal CurrentUser user) {
    Pageable pageable = PageRequest.of(Math.max(page, 1) - 1, Math.min(Math.max(size, 1), 100),
        Sort.by(Sort.Direction.DESC, "salesCount").and(Sort.by(Sort.Direction.DESC, "id")));
    Page<Product> result = products.search(keyword, pageable);
    recordKeyword(keyword, user == null ? null : user.id());
    return new PageResponse<>(result.getTotalElements(), toProductResponses(result.getContent()));
  }

  @GetMapping("/products/recommendations")
  RecommendationResponse recommendations(@AuthenticationPrincipal CurrentUser user,
                                         @RequestParam(defaultValue = "8") int limit) {
    int cappedLimit = Math.min(Math.max(limit, 1), 20);
    List<Product> pool = products.findByStatusOrderBySalesCountDescIdDesc(1, PageRequest.of(0, 200));
    if (user == null) {
      return new RecommendationResponse("fallback", toProductResponses(pool.stream().limit(cappedLimit).toList()));
    }

    List<UserPreference> prefRows = preferences.findByUserIdOrderByWeightDescUpdatedAtDesc(user.id(), PageRequest.of(0, 8));
    List<ProductViewLog> recentViews = viewLogs.findByUserIdOrderByViewedAtDesc(user.id(), PageRequest.of(0, 20));
    Set<Long> viewedCategoryIds = recentViews.stream()
        .map(v -> v.product)
        .filter(Objects::nonNull)
        .map(p -> p.categoryId)
        .collect(Collectors.toCollection(LinkedHashSet::new));
    Set<Long> bought = new HashSet<>(orderItems.findPurchasedProductIds(user.id()));
    boolean hasSignals = !prefRows.isEmpty() || !viewedCategoryIds.isEmpty();
    if (!hasSignals) {
      return new RecommendationResponse("fallback", toProductResponses(topSelling(pool, bought, cappedLimit)));
    }

    Map<Long, String> categoryNames = categories.findAll().stream()
        .collect(Collectors.toMap(c -> c.id, c -> c.name));
    List<String> terms = prefRows.stream()
        .map(p -> p.preferenceTag == null ? "" : p.preferenceTag.trim().toLowerCase(Locale.ROOT))
        .filter(s -> !s.isBlank())
        .toList();

    LinkedHashMap<Long, Product> picked = new LinkedHashMap<>();
    for (Product product : pool) {
      if (picked.size() >= cappedLimit) break;
      if (bought.contains(product.id)) continue;
      String categoryName = categoryNames.getOrDefault(product.categoryId, "");
      if (matchesPreference(product, categoryName, terms) || viewedCategoryIds.contains(product.categoryId)) {
        picked.put(product.id, product);
      }
    }
    for (Product product : pool) {
      if (picked.size() >= cappedLimit) break;
      if (!bought.contains(product.id) && viewedCategoryIds.contains(product.categoryId)) {
        picked.putIfAbsent(product.id, product);
      }
    }
    if (picked.isEmpty()) {
      return new RecommendationResponse("fallback", toProductResponses(topSelling(pool, bought, cappedLimit)));
    }
    for (Product product : pool) {
      if (picked.size() >= cappedLimit) break;
      if (!bought.contains(product.id)) {
        picked.putIfAbsent(product.id, product);
      }
    }
    return new RecommendationResponse("personalized", toProductResponses(new ArrayList<>(picked.values())));
  }

  @GetMapping("/products/{id}")
  ProductResponse detail(@PathVariable Long id, @AuthenticationPrincipal CurrentUser user) {
    Product product = products.findById(id).filter(p -> p.status == 1)
        .orElseThrow(() -> BizException.notFound("商品不存在"));
    if (user != null) {
      recordView(user.id(), product.id);
    }
    return productResponse(product);
  }

  @PostMapping("/products/{id}/view")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void recordProductView(@PathVariable Long id, @AuthenticationPrincipal CurrentUser user) {
    products.findById(id).filter(p -> p.status == 1)
        .orElseThrow(() -> BizException.notFound("商品不存在"));
    ProductViewLog log = new ProductViewLog();
    log.userId = user == null ? null : user.id();
    log.productId = id;
    log.viewedAt = LocalDateTime.now();
    viewLogs.save(log);
  }

  @GetMapping("/products/{id}/reviews")
  PageResponse<ReviewResponse> productReviews(@PathVariable Long id,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "10") int size) {
    products.findById(id).filter(p -> p.status == 1).orElseThrow(() -> BizException.notFound("商品不存在"));
    Pageable pageable = PageRequest.of(Math.max(page, 1) - 1, Math.min(Math.max(size, 1), 50));
    Page<ProductReview> result = reviews.findByProductIdOrderByCreatedAtDesc(id, pageable);
    List<ReviewResponse> list = result.getContent().stream()
        .map(review -> ReviewResponse.from(review, users.findById(review.userId).map(u -> u.username).orElse("用户")))
        .toList();
    return new PageResponse<>(result.getTotalElements(), list);
  }

  @PostMapping("/products/{id}/reviews")
  ResponseEntity<ReviewResponse> createReview(@AuthenticationPrincipal CurrentUser user,
                                              @PathVariable Long id,
                                              @Valid @RequestBody ReviewRequest request) {
    products.findById(id).filter(p -> p.status == 1).orElseThrow(() -> BizException.notFound("商品不存在"));
    Long orderId = orderItems.findCompletedOrderIdsForProduct(user.id(), id, PageRequest.of(0, 10))
        .stream()
        .filter(candidate -> !reviews.existsByUserIdAndProductIdAndOrderId(user.id(), id, candidate))
        .findFirst()
        .orElseThrow(() -> BizException.badRequest("完成购买后才能评价，或该商品已评价"));
    ProductReview review = new ProductReview();
    review.productId = id;
    review.userId = user.id();
    review.orderId = orderId;
    review.rating = request.rating();
    review.content = request.content();
    review.createdAt = LocalDateTime.now();
    ProductReview saved = reviews.save(review);
    String username = users.findById(user.id()).map(u -> u.username).orElse("用户");
    return ResponseEntity.status(HttpStatus.CREATED).body(ReviewResponse.from(saved, username));
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
  @Transactional
  @AdminOperation(action = "product.create", targetType = "product")
  ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
    Product product = new Product();
    apply(product, request);
    product.salesCount = 0;
    product.version = 0;
    product.createdAt = LocalDateTime.now();
    Product saved = products.save(product);
    applyTags(saved.id, request.tagIds());
    return ResponseEntity.status(HttpStatus.CREATED).body(productResponse(saved));
  }

  @PostMapping(value = "/products/upload-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasRole('ADMIN')")
  @AdminOperation(action = "product.image.upload", targetType = "product")
  ResponseEntity<ImageUploadResponse> uploadImage(@RequestParam("file") MultipartFile file) {
    if (file.isEmpty() || file.getSize() > 5L * 1024 * 1024) {
      throw BizException.badRequest("图片文件不能为空且不能超过5MB");
    }
    String extension = imageExtension(file.getContentType());
    if (extension == null) {
      throw BizException.badRequest("仅支持 JPG、PNG、GIF 或 WEBP 图片");
    }
    String filename = UUID.randomUUID().toString().replace("-", "") + extension;
    try {
      Files.createDirectories(uploadDirectory);
      try (InputStream input = file.getInputStream()) {
        Files.copy(input, uploadDirectory.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException ex) {
      throw new BizException(HttpStatus.INTERNAL_SERVER_ERROR, "图片保存失败");
    }
    return ResponseEntity.status(HttpStatus.CREATED).body(new ImageUploadResponse("/uploads/" + filename));
  }

  @PutMapping("/products/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @Transactional
  @AdminOperation(action = "product.update", targetType = "product")
  ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
    Product product = products.findById(id).orElseThrow(() -> BizException.notFound("商品不存在"));
    apply(product, request);
    Product saved = products.save(product);
    applyTags(saved.id, request.tagIds());
    return productResponse(saved);
  }

  @DeleteMapping("/products/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @AdminOperation(action = "product.delete", targetType = "product")
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

  private List<Long> categoryIdsWithChildren(Long categoryId) {
    List<Long> ids = new ArrayList<>();
    ids.add(categoryId);
    List<Category> children = categories.findByParentIdOrderBySortOrderAscIdAsc(categoryId);
    for (Category child : children) {
      ids.addAll(categoryIdsWithChildren(child.id));
    }
    return ids;
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

  private String imageExtension(String contentType) {
    if (contentType == null) return null;
    return switch (contentType.toLowerCase(Locale.ROOT)) {
      case "image/jpeg" -> ".jpg";
      case "image/png" -> ".png";
      case "image/gif" -> ".gif";
      case "image/webp" -> ".webp";
      default -> null;
    };
  }

  private void applyTags(Long productId, List<Long> tagIds) {
    if (tagIds == null) return;
    List<Long> uniqueIds = tagIds.stream().filter(Objects::nonNull).distinct().toList();
    List<ProductTag> found = tags.findAllById(uniqueIds);
    if (found.size() != uniqueIds.size()) {
      throw BizException.badRequest("商品标签不存在");
    }
    tagRelations.deleteByProductId(productId);
    List<ProductTagRelation> relations = uniqueIds.stream().map(tagId -> {
      ProductTagRelation relation = new ProductTagRelation();
      relation.productId = productId;
      relation.tagId = tagId;
      return relation;
    }).toList();
    tagRelations.saveAll(relations);
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

  private ProductResponse productResponse(Product product) {
    Double avgRating = reviews.avgRatingByProductId(product.id);
    Long reviewCount = reviews.countByProductId(product.id);
    return new ProductResponse(product.id, product.categoryId, product.name, product.description, product.price,
        product.stock, product.salesCount, product.imageUrl, product.status, product.version,
        Math.round((avgRating == null ? 0.0 : avgRating) * 10.0) / 10.0, reviewCount, tagsForProduct(product.id));
  }

  private List<ProductResponse> toProductResponses(List<Product> productList) {
    Map<Long, List<TagResponse>> tagsByProduct = tagsByProductIds(productList.stream().map(p -> p.id).toList());
    return productList.stream()
        .map(p -> ProductResponse.from(p, tagsByProduct.getOrDefault(p.id, List.of())))
        .toList();
  }

  private List<TagResponse> tagsForProduct(Long productId) {
    return tagRelations.findByProductId(productId).stream()
        .filter(r -> r.tag != null)
        .map(r -> new TagResponse(r.tag.id, r.tag.name))
        .toList();
  }

  private Map<Long, List<TagResponse>> tagsByProductIds(List<Long> productIds) {
    if (productIds.isEmpty()) return Map.of();
    return tagRelations.findByProductIdIn(productIds).stream()
        .filter(r -> r.tag != null)
        .collect(Collectors.groupingBy(
            r -> r.productId,
            Collectors.mapping(r -> new TagResponse(r.tag.id, r.tag.name), Collectors.toList())
        ));
  }

  private boolean matchesPreference(Product product, String categoryName, List<String> terms) {
    String text = String.join(" ",
        product.name == null ? "" : product.name,
        product.description == null ? "" : product.description,
        categoryName == null ? "" : categoryName).toLowerCase(Locale.ROOT);
    return terms.stream().anyMatch(text::contains);
  }

  private List<Product> topSelling(List<Product> pool, Set<Long> excludedIds, int limit) {
    return pool.stream()
        .filter(p -> !excludedIds.contains(p.id))
        .limit(limit)
        .toList();
  }

  private void recordKeyword(String keyword, Long userId) {
    String clean = keyword == null ? "" : keyword.trim();
    if (clean.isBlank()) return;
    SearchKeywordLog log = new SearchKeywordLog();
    log.keyword = clean.length() > 64 ? clean.substring(0, 64) : clean;
    log.userId = userId;
    log.isBlocked = 0;
    log.searchedAt = LocalDateTime.now();
    keywordLogs.save(log);
  }
}

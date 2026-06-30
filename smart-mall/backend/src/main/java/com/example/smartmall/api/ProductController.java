package com.example.smartmall.api;

import com.example.smartmall.domain.*;
import com.example.smartmall.repo.*;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static com.example.smartmall.api.ApiSupport.*;

@RestController
@RequestMapping("/api")
public class ProductController {
  private final CategoryRepository categories;
  private final ProductRepository products;

  public ProductController(CategoryRepository categories, ProductRepository products) {
    this.categories = categories;
    this.products = products;
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
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "20") int size) {
    Pageable pageable = PageRequest.of(Math.max(page, 1) - 1, Math.min(Math.max(size, 1), 100),
        Sort.by(Sort.Direction.DESC, "id"));
    Page<Product> result = categoryId == null
        ? products.findAll(pageable)
        : products.findByCategoryId(categoryId, pageable);
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
  ProductResponse detail(@PathVariable Long id) {
    Product product = products.findById(id).filter(p -> p.status == 1)
        .orElseThrow(() -> BizException.notFound("商品不存在"));
    return ProductResponse.from(product);
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
}

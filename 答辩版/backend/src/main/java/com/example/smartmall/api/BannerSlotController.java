package com.example.smartmall.api;

import com.example.smartmall.audit.AdminOperation;
import com.example.smartmall.domain.*;
import com.example.smartmall.repo.*;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static com.example.smartmall.api.ApiSupport.*;

@RestController
@RequestMapping("/api")
public class BannerSlotController {
  private final BannerSlotRepository bannerSlots;
  private final ProductRepository products;

  public BannerSlotController(BannerSlotRepository bannerSlots, ProductRepository products) {
    this.bannerSlots = bannerSlots;
    this.products = products;
  }

  @GetMapping("/banner-slots/active")
  List<ProductResponse> active() {
    return bannerSlots.findByIsActiveOrderBySortOrderAscIdAsc(1).stream()
        .filter(slot -> slot.product != null && slot.product.status == 1)
        .map(slot -> ProductResponse.from(slot.product))
        .toList();
  }

  @GetMapping("/admin/banner-slots")
  @PreAuthorize("hasRole('ADMIN')")
  List<BannerSlotResponse> adminList() {
    return bannerSlots.findAllByOrderBySortOrderAscIdAsc().stream().map(BannerSlotResponse::from).toList();
  }

  @PostMapping("/admin/banner-slots")
  @PreAuthorize("hasRole('ADMIN')")
  @AdminOperation(action = "banner.create", targetType = "banner_slot")
  ResponseEntity<BannerSlotResponse> create(@Valid @RequestBody BannerSlotRequest request) {
    BannerSlot slot = new BannerSlot();
    apply(slot, request);
    slot.createdAt = LocalDateTime.now();
    return ResponseEntity.status(HttpStatus.CREATED).body(BannerSlotResponse.from(bannerSlots.save(slot)));
  }

  @PutMapping("/admin/banner-slots/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @AdminOperation(action = "banner.update", targetType = "banner_slot")
  BannerSlotResponse update(@PathVariable Long id, @Valid @RequestBody BannerSlotRequest request) {
    BannerSlot slot = bannerSlots.findById(id).orElseThrow(() -> BizException.notFound("推荐位不存在"));
    apply(slot, request);
    return BannerSlotResponse.from(bannerSlots.save(slot));
  }

  @DeleteMapping("/admin/banner-slots/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @AdminOperation(action = "banner.delete", targetType = "banner_slot")
  Map<String, Object> delete(@PathVariable Long id) {
    BannerSlot slot = bannerSlots.findById(id).orElseThrow(() -> BizException.notFound("推荐位不存在"));
    bannerSlots.delete(slot);
    return Map.of("ok", true);
  }

  private void apply(BannerSlot slot, BannerSlotRequest request) {
    Product product = products.findById(request.productId()).orElseThrow(() -> BizException.badRequest("商品不存在"));
    slot.productId = request.productId();
    slot.product = product;
    slot.sortOrder = request.sortOrder() == null ? 0 : request.sortOrder();
    slot.isActive = Boolean.FALSE.equals(request.active()) ? 0 : 1;
  }
}

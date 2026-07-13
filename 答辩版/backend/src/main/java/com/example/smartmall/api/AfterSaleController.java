package com.example.smartmall.api;

import com.example.smartmall.api.ApiSupport.*;
import com.example.smartmall.audit.AdminOperation;
import com.example.smartmall.security.CurrentUser;
import com.example.smartmall.service.AfterSaleService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AfterSaleController {
  private final AfterSaleService afterSaleService;

  public AfterSaleController(AfterSaleService afterSaleService) {
    this.afterSaleService = afterSaleService;
  }

  @PostMapping("/orders/{id}/after-sale")
  ResponseEntity<AfterSaleResponse> apply(@AuthenticationPrincipal CurrentUser user,
                                          @PathVariable Long id,
                                          @Valid @RequestBody AfterSaleApplyRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(afterSaleService.apply(user.id(), id, request));
  }

  @GetMapping("/user/after-sales")
  List<AfterSaleResponse> myRequests(@AuthenticationPrincipal CurrentUser user) {
    return afterSaleService.myRequests(user.id());
  }

  @GetMapping("/admin/after-sales")
  @PreAuthorize("hasRole('ADMIN')")
  PageResponse<AfterSaleResponse> adminList(@RequestParam(defaultValue = "all") String status,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int size) {
    return PageResponse.from(afterSaleService.adminList(status, page, size));
  }

  @PutMapping("/admin/after-sales/{id}/approve")
  @PreAuthorize("hasRole('ADMIN')")
  @AdminOperation(action = "after-sale.approve", targetType = "after_sale_request")
  AfterSaleResponse approve(@PathVariable Long id,
                            @RequestBody(required = false) AfterSaleHandleRequest request) {
    return afterSaleService.approve(id, request);
  }

  @PutMapping("/admin/after-sales/{id}/reject")
  @PreAuthorize("hasRole('ADMIN')")
  @AdminOperation(action = "after-sale.reject", targetType = "after_sale_request")
  AfterSaleResponse reject(@PathVariable Long id,
                           @RequestBody(required = false) AfterSaleHandleRequest request) {
    return afterSaleService.reject(id, request);
  }
}

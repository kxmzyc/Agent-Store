package com.example.smartmall.api;

import com.example.smartmall.domain.Order;
import com.example.smartmall.repo.*;
import com.example.smartmall.service.OrderService;
import java.math.BigDecimal;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static com.example.smartmall.api.ApiSupport.*;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
  private final UserRepository users;
  private final ProductRepository products;
  private final OrderRepository orders;
  private final OrderService orderService;

  public AdminController(UserRepository users, ProductRepository products, OrderRepository orders, OrderService orderService) {
    this.users = users;
    this.products = products;
    this.orders = orders;
    this.orderService = orderService;
  }

  @GetMapping("/dashboard")
  AdminDashboardResponse dashboard() {
    AdminMetricResponse metrics = new AdminMetricResponse(
        users.count(),
        products.countByStatus(1),
        products.countByStatusAndStock(1, 0),
        orders.count(),
        orders.countByStatus("PENDING_PAYMENT"),
        orders.countByStatus("PAID"),
        orders.countByStatus("SHIPPED"),
        orders.countByStatus("COMPLETED"),
        safeAmount(orders.sumEffectiveAmount())
    );

    return new AdminDashboardResponse(
        metrics,
        products.findByStatusAndStockLessThanEqualOrderByStockAscSalesCountDescIdDesc(1, 5, PageRequest.of(0, 6))
            .stream().map(AdminProductBrief::from).toList(),
        products.findByStatusOrderBySalesCountDescIdDesc(1, PageRequest.of(0, 6))
            .stream().map(AdminProductBrief::from).toList(),
        orders.findRecent(PageRequest.of(0, 6))
            .stream().map(AdminOrderBrief::from).toList()
    );
  }

  @GetMapping("/orders")
  PageResponse<OrderResponse> orders(@RequestParam(defaultValue = "all") String status,
                                     @RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "20") int size) {
    Pageable pageable = PageRequest.of(
        Math.max(page, 1) - 1,
        Math.min(Math.max(size, 1), 100),
        Sort.by(Sort.Direction.DESC, "createdAt")
    );
    Page<Order> result = "all".equalsIgnoreCase(status)
        ? orders.findAllByOrderByCreatedAtDesc(pageable)
        : orders.findByStatusOrderByCreatedAtDesc(status, pageable);
    return new PageResponse<>(result.getTotalElements(), result.getContent().stream().map(orderService::toResponse).toList());
  }

  private BigDecimal safeAmount(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }
}

package com.example.smartmall.api;

import com.example.smartmall.domain.Order;
import com.example.smartmall.domain.OrderItem;
import com.example.smartmall.repo.*;
import com.example.smartmall.service.OrderService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
  private final OrderItemRepository orderItems;
  private final CategoryRepository categories;
  private final OrderService orderService;

  public AdminController(UserRepository users, ProductRepository products, OrderRepository orders,
                         OrderItemRepository orderItems, CategoryRepository categories, OrderService orderService) {
    this.users = users;
    this.products = products;
    this.orders = orders;
    this.orderItems = orderItems;
    this.categories = categories;
    this.orderService = orderService;
  }

  @GetMapping("/dashboard")
  AdminDashboardResponse dashboard() {
    LocalDate today = LocalDate.now();
    LocalDateTime todayStart = today.atStartOfDay();
    LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();
    LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
    LocalDateTime nextMonthStart = today.withDayOfMonth(1).plusMonths(1).atStartOfDay();
    Map<Long, String> categoryNames = categories.findAll().stream()
        .collect(Collectors.toMap(c -> c.id, c -> c.name));
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
        products.findByStatusAndStockLessThanEqualOrderByStockAscSalesCountDescIdDesc(1, 10, PageRequest.of(0, 8))
            .stream().map(p -> AdminProductBrief.from(p, categoryNames.get(p.categoryId))).toList(),
        products.findByStatusOrderBySalesCountDescIdDesc(1, PageRequest.of(0, 6))
            .stream().map(p -> AdminProductBrief.from(p, categoryNames.get(p.categoryId))).toList(),
        orders.findRecent(PageRequest.of(0, 6))
            .stream().map(AdminOrderBrief::from).toList(),
        orders.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(todayStart, tomorrowStart),
        safeAmount(orders.sumEffectiveAmountBetween(monthStart, nextMonthStart)),
        products.countByStatus(1),
        users.count(),
        last7DaysOrders(today.minusDays(6)),
        categoryTopSales()
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

  @GetMapping("/orders/export")
  public void exportOrders(HttpServletResponse response) throws IOException {
    response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    response.setHeader("Content-Disposition", "attachment; filename=orders_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".xlsx");
    try (Workbook workbook = new XSSFWorkbook()) {
      Sheet sheet = workbook.createSheet("orders");
      Row head = sheet.createRow(0);
      String[] titles = {"订单号", "用户名", "商品明细", "实付金额", "优惠金额", "下单时间", "状态"};
      for (int i = 0; i < titles.length; i++) {
        head.createCell(i).setCellValue(titles[i]);
      }
      List<Order> all = orders.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 10000)).getContent();
      int rowIndex = 1;
      for (Order order : all) {
        Row row = sheet.createRow(rowIndex++);
        row.createCell(0).setCellValue(order.orderNo);
        row.createCell(1).setCellValue(users.findById(order.userId).map(u -> u.username).orElse("用户" + order.userId));
        row.createCell(2).setCellValue(orderItems.findByOrderId(order.id).stream().map(this::itemText).collect(Collectors.joining("；")));
        row.createCell(3).setCellValue(safeAmount(order.totalAmount).doubleValue());
        row.createCell(4).setCellValue(safeAmount(order.discountAmount).doubleValue());
        row.createCell(5).setCellValue(order.createdAt == null ? "" : order.createdAt.toString().replace('T', ' '));
        row.createCell(6).setCellValue(order.status);
      }
      for (int i = 0; i < titles.length; i++) sheet.autoSizeColumn(i);
      workbook.write(response.getOutputStream());
    }
  }

  private BigDecimal safeAmount(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }

  private List<DailyOrderCount> last7DaysOrders(LocalDate startDate) {
    Map<String, Long> counts = orders.findByCreatedAtGreaterThanEqual(startDate.atStartOfDay()).stream()
        .collect(Collectors.groupingBy(o -> o.createdAt.toLocalDate().toString(), Collectors.counting()));
    List<DailyOrderCount> result = new ArrayList<>();
    for (int i = 0; i < 7; i++) {
      String date = startDate.plusDays(i).toString();
      result.add(new DailyOrderCount(date, counts.getOrDefault(date, 0L)));
    }
    return result;
  }

  private List<CategorySales> categoryTopSales() {
    return orderItems.categoryTopSales(PageRequest.of(0, 5)).stream()
        .map(row -> new CategorySales(String.valueOf(row[0]), ((Number) row[1]).longValue()))
        .toList();
  }

  private String itemText(OrderItem item) {
    return item.productNameSnapshot + "×" + item.quantity;
  }
}

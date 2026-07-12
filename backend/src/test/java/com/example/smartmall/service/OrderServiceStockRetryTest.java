package com.example.smartmall.service;

import com.example.smartmall.api.BizException;
import com.example.smartmall.domain.Order;
import com.example.smartmall.domain.OrderItem;
import com.example.smartmall.domain.Product;
import com.example.smartmall.repo.CartRepository;
import com.example.smartmall.repo.OrderItemRepository;
import com.example.smartmall.repo.OrderRepository;
import com.example.smartmall.repo.ProductRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderServiceStockRetryTest {
  private final CartRepository carts = mock(CartRepository.class);
  private final ProductRepository products = mock(ProductRepository.class);
  private final OrderRepository orders = mock(OrderRepository.class);
  private final OrderItemRepository orderItems = mock(OrderItemRepository.class);
  private final CouponService couponService = mock(CouponService.class);
  private final PointService pointService = mock(PointService.class);
  private final TransactionOperations tx = new TransactionOperations() {
    @Override
    public <T> T execute(TransactionCallback<T> action) {
      return action.doInTransaction(new SimpleTransactionStatus());
    }
  };
  private OrderService service;

  @BeforeEach
  void setUp() {
    service = new OrderService(carts, products, orders, orderItems, couponService, pointService, tx);
    AtomicLong orderIds = new AtomicLong(100);
    when(orders.save(any(Order.class))).thenAnswer(invocation -> {
      Order order = invocation.getArgument(0);
      if (order.id == null) {
        order.id = orderIds.getAndIncrement();
      }
      return order;
    });
    when(orderItems.save(any(OrderItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(couponService.useCoupon(anyLong(), nullable(Long.class), any(BigDecimal.class), anyLong()))
        .thenReturn(new CouponService.CouponApplyResult(BigDecimal.ZERO));
    when(pointService.usePoints(anyLong(), any(BigDecimal.class), anyBoolean()))
        .thenReturn(new PointService.PointDiscount(BigDecimal.ZERO, 0));
  }

  @Test
  void repositoryFirstDeductSuccessCreatesOrder() {
    Product product = product(7L, 1, 0);
    when(products.findById(7L)).thenReturn(Optional.of(product));
    when(products.deductStock(7L, 1, 0)).thenReturn(1);

    var response = service.createDirect(1L, 7L, 1, "test address", null, false);

    assertThat(response.orderId()).isEqualTo(100L);
    assertThat(response.orderNo()).hasSize(32);
    assertThat(response.totalAmount()).isEqualByComparingTo("10.00");
    verify(products).deductStock(7L, 1, 0);
  }

  @Test
  void repositoryConflictReloadsFreshVersionAndSucceeds() {
    Product stale = product(7L, 1, 0);
    Product fresh = product(7L, 1, 1);
    when(products.findById(7L)).thenReturn(Optional.of(stale), Optional.of(fresh));
    when(products.deductStock(7L, 1, 0)).thenReturn(0);
    when(products.deductStock(7L, 1, 1)).thenReturn(1);

    var response = service.createDirect(1L, 7L, 1, "test address", null, false);

    assertThat(response.orderId()).isEqualTo(100L);
    verify(products).deductStock(7L, 1, 0);
    verify(products).deductStock(7L, 1, 1);
  }

  @Test
  void repositoryConflictThenReloadedStockInsufficientReturnsBusinessError() {
    Product stale = product(7L, 1, 0);
    Product depleted = product(7L, 0, 1);
    when(products.findById(7L)).thenReturn(Optional.of(stale), Optional.of(depleted));
    when(products.deductStock(7L, 1, 0)).thenReturn(0);

    assertThatThrownBy(() -> service.createDirect(1L, 7L, 1, "test address", null, false))
        .isInstanceOfSatisfying(BizException.class, ex -> {
          assertThat(ex.status).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(ex.getMessage()).contains("库存不足");
        });
  }

  @Test
  void retryExhaustedWhileStockRemainsReturnsConflict() {
    Product product = product(7L, 1, 0);
    when(products.findById(7L)).thenReturn(Optional.of(product));
    when(products.deductStock(7L, 1, 0)).thenReturn(0);

    assertThatThrownBy(() -> service.createDirect(1L, 7L, 1, "test address", null, false))
        .isInstanceOfSatisfying(BizException.class, ex -> {
          assertThat(ex.status).isEqualTo(HttpStatus.CONFLICT);
          assertThat(ex.getMessage()).contains("库存繁忙");
        });
    verify(products, atLeast(2)).deductStock(7L, 1, 0);
  }

  private Product product(Long id, int stock, int version) {
    Product product = new Product();
    product.id = id;
    product.categoryId = 1L;
    product.name = "retry product";
    product.description = "test";
    product.price = new BigDecimal("10.00");
    product.stock = stock;
    product.salesCount = 0;
    product.status = 1;
    product.version = version;
    product.createdAt = LocalDateTime.now();
    return product;
  }
}

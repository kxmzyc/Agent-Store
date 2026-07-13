package com.example.smartmall.repo;

import com.example.smartmall.domain.OrderItem;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
  List<OrderItem> findByOrderId(Long orderId);

  @Query("""
      select i.orderId from OrderItem i
      join i.order o
      where o.userId = :userId and o.status = 'COMPLETED' and i.productId = :productId
      order by o.createdAt desc
      """)
  List<Long> findCompletedOrderIdsForProduct(@Param("userId") Long userId,
                                             @Param("productId") Long productId,
                                             Pageable pageable);

  @Query("""
      select c.name, coalesce(sum(i.quantity), 0)
      from OrderItem i
      join i.order o
      join Product p on p.id = i.productId
      join Category c on c.id = p.categoryId
      where o.status not in ('CANCELLED', 'REFUNDED')
      group by c.name
      order by coalesce(sum(i.quantity), 0) desc
      """)
  List<Object[]> categoryTopSales(Pageable pageable);

  @Query("""
      select distinct i.productId
      from OrderItem i
      join i.order o
      where o.userId = :userId and o.status not in ('CANCELLED', 'REFUNDED')
      """)
  List<Long> findPurchasedProductIds(@Param("userId") Long userId);
}

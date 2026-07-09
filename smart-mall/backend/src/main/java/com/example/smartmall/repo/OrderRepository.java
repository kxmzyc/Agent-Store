package com.example.smartmall.repo;

import com.example.smartmall.domain.Order;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {
  List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);
  List<Order> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);
  Optional<Order> findByIdAndUserId(Long id, Long userId);
  long countByStatus(String status);
  long countByUserIdAndStatus(Long userId, String status);
  long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(LocalDateTime start, LocalDateTime end);
  Page<Order> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);
  Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);
  List<Order> findByCreatedAtGreaterThanEqual(LocalDateTime start);

  @Query("select coalesce(sum(o.totalAmount), 0) from Order o where o.status not in ('CANCELLED', 'REFUNDED')")
  BigDecimal sumEffectiveAmount();

  @Query("""
      select coalesce(sum(o.totalAmount), 0) from Order o
      where o.status not in ('CANCELLED', 'REFUNDED') and o.createdAt >= :start and o.createdAt < :end
      """)
  BigDecimal sumEffectiveAmountBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

  @Query("select o from Order o where o.userId = :userId order by o.createdAt desc")
  List<Order> findRecentByUserId(Long userId, Pageable pageable);

  @Query("select o from Order o order by o.createdAt desc")
  List<Order> findRecent(Pageable pageable);

  @Query(value = """
      select o as orderEntity, u.username as username, u.phone as phone
      from Order o join User u on u.id = o.userId
      where (:status = 'all' or o.status = :status)
      order by o.createdAt desc
      """,
      countQuery = """
      select count(o)
      from Order o join User u on u.id = o.userId
      where (:status = 'all' or o.status = :status)
      """)
  Page<AdminOrderView> findAdminOrders(@Param("status") String status, Pageable pageable);

  interface AdminOrderView {
    Order getOrderEntity();
    String getUsername();
    String getPhone();
  }
}

package com.example.smartmall.repo;

import com.example.smartmall.domain.Order;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface OrderRepository extends JpaRepository<Order, Long> {
  List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);
  List<Order> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);
  Optional<Order> findByIdAndUserId(Long id, Long userId);
  long countByStatus(String status);
  long countByUserIdAndStatus(Long userId, String status);
  Page<Order> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);
  Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);

  @Query("select coalesce(sum(o.totalAmount), 0) from Order o where o.status <> 'CANCELLED'")
  BigDecimal sumEffectiveAmount();

  @Query("select o from Order o where o.userId = :userId order by o.createdAt desc")
  List<Order> findRecentByUserId(Long userId, Pageable pageable);

  @Query("select o from Order o order by o.createdAt desc")
  List<Order> findRecent(Pageable pageable);
}

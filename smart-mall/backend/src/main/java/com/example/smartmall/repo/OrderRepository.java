package com.example.smartmall.repo;

import com.example.smartmall.domain.Order;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface OrderRepository extends JpaRepository<Order, Long> {
  List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);
  List<Order> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);
  Optional<Order> findByIdAndUserId(Long id, Long userId);

  @Query("select o from Order o where o.userId = :userId order by o.createdAt desc")
  List<Order> findRecentByUserId(Long userId, Pageable pageable);
}

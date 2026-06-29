package com.example.smartmall.repo;

import com.example.smartmall.domain.CartItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartRepository extends JpaRepository<CartItem, Long> {
  @EntityGraph(attributePaths = "product")
  List<CartItem> findByUserIdOrderByCreatedAtDesc(Long userId);
  @EntityGraph(attributePaths = "product")
  List<CartItem> findByUserIdAndIdIn(Long userId, List<Long> ids);
  Optional<CartItem> findByUserIdAndProductId(Long userId, Long productId);
  @EntityGraph(attributePaths = "product")
  Optional<CartItem> findByIdAndUserId(Long id, Long userId);
  void deleteByUserIdAndIdIn(Long userId, List<Long> ids);
}

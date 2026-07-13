package com.example.smartmall.repo;

import com.example.smartmall.domain.ProductViewHistory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductViewHistoryRepository extends JpaRepository<ProductViewHistory, Long> {
  long countByUserId(Long userId);
  Optional<ProductViewHistory> findByUserIdAndProductId(Long userId, Long productId);
  void deleteByUserId(Long userId);

  @EntityGraph(attributePaths = "product")
  List<ProductViewHistory> findByUserIdOrderByLastViewedAtDesc(Long userId, Pageable pageable);
}

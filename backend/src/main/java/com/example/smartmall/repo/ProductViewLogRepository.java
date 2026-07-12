package com.example.smartmall.repo;

import com.example.smartmall.domain.ProductViewLog;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductViewLogRepository extends JpaRepository<ProductViewLog, Long> {
  @EntityGraph(attributePaths = "product")
  List<ProductViewLog> findByUserIdOrderByViewedAtDesc(Long userId, Pageable pageable);
}

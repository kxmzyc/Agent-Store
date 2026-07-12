package com.example.smartmall.repo;

import com.example.smartmall.domain.ProductFavorite;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductFavoriteRepository extends JpaRepository<ProductFavorite, Long> {
  boolean existsByUserIdAndProductId(Long userId, Long productId);
  long countByUserId(Long userId);
  Optional<ProductFavorite> findByUserIdAndProductId(Long userId, Long productId);
  void deleteByUserIdAndProductId(Long userId, Long productId);

  @EntityGraph(attributePaths = "product")
  List<ProductFavorite> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}

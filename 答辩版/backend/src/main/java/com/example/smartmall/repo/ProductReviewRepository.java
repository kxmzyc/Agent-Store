package com.example.smartmall.repo;

import com.example.smartmall.domain.ProductReview;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {
  Page<ProductReview> findByProductIdOrderByCreatedAtDesc(Long productId, Pageable pageable);
  boolean existsByUserIdAndProductIdAndOrderId(Long userId, Long productId, Long orderId);
  long countByProductId(Long productId);

  @Query("select coalesce(avg(r.rating), 0) from ProductReview r where r.productId = :productId")
  Double avgRatingByProductId(@Param("productId") Long productId);
}

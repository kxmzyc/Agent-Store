package com.example.smartmall.repo;

import com.example.smartmall.domain.Coupon;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CouponRepository extends JpaRepository<Coupon, Long> {
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("update Coupon c set c.remainCount = c.remainCount - 1 where c.id = :id and c.remainCount > 0")
  int claimOne(@Param("id") Long id);
}

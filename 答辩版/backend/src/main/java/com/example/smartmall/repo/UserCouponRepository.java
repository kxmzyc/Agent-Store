package com.example.smartmall.repo;

import com.example.smartmall.domain.UserCoupon;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserCouponRepository extends JpaRepository<UserCoupon, Long> {
  boolean existsByUserIdAndCouponId(Long userId, Long couponId);
  List<UserCoupon> findByUserIdAndCouponId(Long userId, Long couponId);
  List<UserCoupon> findByUserIdOrderByCreatedAtDesc(Long userId);
  List<UserCoupon> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, Integer status);
  Optional<UserCoupon> findByIdAndUserId(Long id, Long userId);
  List<UserCoupon> findByUsedOrderId(Long usedOrderId);
  List<UserCoupon> findByUserIdAndStatusAndExpireAtGreaterThanEqualOrderByCreatedAtDesc(Long userId, Integer status, LocalDate date);

  @Query("select uc from UserCoupon uc left join fetch uc.coupon where uc.userId = :userId order by uc.createdAt desc")
  List<UserCoupon> findWithCouponByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);

  @Query("select uc from UserCoupon uc left join fetch uc.coupon where uc.userId = :userId and uc.status = :status order by uc.createdAt desc")
  List<UserCoupon> findWithCouponByUserIdAndStatusOrderByCreatedAtDesc(@Param("userId") Long userId, @Param("status") Integer status);

  @Query("select uc from UserCoupon uc left join fetch uc.coupon where uc.id = :id and uc.userId = :userId")
  Optional<UserCoupon> findWithCouponByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

  @Query("""
      select uc from UserCoupon uc
      left join fetch uc.coupon
      where uc.userId = :userId and uc.status = :status and uc.expireAt >= :date
      order by uc.createdAt desc
      """)
  List<UserCoupon> findUsableWithCoupon(@Param("userId") Long userId, @Param("status") Integer status, @Param("date") LocalDate date);
}

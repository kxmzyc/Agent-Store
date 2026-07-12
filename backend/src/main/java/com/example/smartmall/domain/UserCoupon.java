package com.example.smartmall.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_coupon")
public class UserCoupon {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;
  @Column(name = "user_id")
  public Long userId;
  @Column(name = "coupon_id")
  public Long couponId;
  public Integer status;
  @Column(name = "expire_at")
  public LocalDate expireAt;
  @Column(name = "used_order_id")
  public Long usedOrderId;
  @Column(name = "created_at")
  public LocalDateTime createdAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "coupon_id", insertable = false, updatable = false)
  public Coupon coupon;
}

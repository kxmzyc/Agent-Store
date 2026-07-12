package com.example.smartmall.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "coupon")
public class Coupon {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;
  public String name;
  public Integer type;
  public BigDecimal threshold;
  public BigDecimal discount;
  @Column(name = "total_count")
  public Integer totalCount;
  @Column(name = "remain_count")
  public Integer remainCount;
  @Column(name = "valid_days")
  public Integer validDays;
  @Column(name = "created_at")
  public LocalDateTime createdAt;
}

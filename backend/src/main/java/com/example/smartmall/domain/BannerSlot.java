package com.example.smartmall.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "banner_slot")
public class BannerSlot {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;
  @Column(name = "product_id")
  public Long productId;
  @Column(name = "sort_order")
  public Integer sortOrder;
  @Column(name = "is_active")
  public Integer isActive;
  @Column(name = "created_at")
  public LocalDateTime createdAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "product_id", insertable = false, updatable = false)
  public Product product;
}

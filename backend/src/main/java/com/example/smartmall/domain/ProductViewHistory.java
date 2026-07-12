package com.example.smartmall.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_view_history",
    uniqueConstraints = @UniqueConstraint(name = "uk_view_history_user_product", columnNames = {"user_id", "product_id"}))
public class ProductViewHistory {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;
  @Column(name = "user_id")
  public Long userId;
  @Column(name = "product_id")
  public Long productId;
  @Column(name = "view_count")
  public Integer viewCount;
  @Column(name = "created_at")
  public LocalDateTime createdAt;
  @Column(name = "last_viewed_at")
  public LocalDateTime lastViewedAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "product_id", insertable = false, updatable = false)
  public Product product;
}

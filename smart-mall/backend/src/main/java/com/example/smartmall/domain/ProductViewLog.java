package com.example.smartmall.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_view_log")
public class ProductViewLog {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;
  @Column(name = "user_id")
  public Long userId;
  @Column(name = "product_id")
  public Long productId;
  @Column(name = "viewed_at")
  public LocalDateTime viewedAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "product_id", insertable = false, updatable = false)
  public Product product;
}

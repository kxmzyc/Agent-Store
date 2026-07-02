package com.example.smartmall.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_favorite",
    uniqueConstraints = @UniqueConstraint(name = "uk_favorite_user_product", columnNames = {"user_id", "product_id"}))
public class ProductFavorite {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;
  @Column(name = "user_id")
  public Long userId;
  @Column(name = "product_id")
  public Long productId;
  @Column(name = "created_at")
  public LocalDateTime createdAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "product_id", insertable = false, updatable = false)
  public Product product;
}

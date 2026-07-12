package com.example.smartmall.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_review")
public class ProductReview {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;
  @Column(name = "product_id")
  public Long productId;
  @Column(name = "user_id")
  public Long userId;
  @Column(name = "order_id")
  public Long orderId;
  public Integer rating;
  public String content;
  @Column(name = "created_at")
  public LocalDateTime createdAt;
}

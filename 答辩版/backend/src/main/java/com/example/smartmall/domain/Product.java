package com.example.smartmall.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "product")
public class Product {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;
  @Column(name = "category_id")
  public Long categoryId;
  public String name;
  @Column(columnDefinition = "TEXT")
  public String description;
  public BigDecimal price;
  public Integer stock;
  @Column(name = "sales_count")
  public Integer salesCount;
  @Column(name = "image_url")
  public String imageUrl;
  public Integer status;
  public Integer version;
  @Column(name = "created_at")
  public LocalDateTime createdAt;
}

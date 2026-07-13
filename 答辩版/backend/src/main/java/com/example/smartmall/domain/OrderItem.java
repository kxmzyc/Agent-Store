package com.example.smartmall.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "order_item")
public class OrderItem {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;
  @Column(name = "order_id")
  public Long orderId;
  @Column(name = "product_id")
  public Long productId;
  @Column(name = "product_name_snapshot")
  public String productNameSnapshot;
  @Column(name = "price_snapshot")
  public BigDecimal priceSnapshot;
  public Integer quantity;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "order_id", insertable = false, updatable = false)
  public Order order;
}

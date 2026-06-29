package com.example.smartmall.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "`order`")
public class Order {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;
  @Column(name = "order_no")
  public String orderNo;
  @Column(name = "user_id")
  public Long userId;
  @Column(name = "total_amount")
  public BigDecimal totalAmount;
  public String status;
  @Column(name = "shipping_address")
  public String shippingAddress;
  @Column(name = "created_at")
  public LocalDateTime createdAt;
  @Column(name = "paid_at")
  public LocalDateTime paidAt;

  @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
  public List<OrderItem> items = new ArrayList<>();
}

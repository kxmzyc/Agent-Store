package com.example.smartmall.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "after_sale_request")
public class AfterSaleRequest {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;
  @Column(name = "order_id")
  public Long orderId;
  @Column(name = "user_id")
  public Long userId;
  public Integer type;
  public String reason;
  public Integer status;
  @Column(name = "refund_amount")
  public BigDecimal refundAmount;
  @Column(name = "handle_remark")
  public String handleRemark;
  @Column(name = "created_at")
  public LocalDateTime createdAt;
  @Column(name = "handled_at")
  public LocalDateTime handledAt;
}

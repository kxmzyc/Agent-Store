package com.example.smartmall.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "shipping_address")
public class ShippingAddress {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;
  @Column(name = "user_id")
  public Long userId;
  @Column(name = "receiver_name")
  public String receiverName;
  public String phone;
  public String province;
  public String city;
  public String district;
  @Column(name = "detail_address")
  public String detailAddress;
  @Column(name = "is_default")
  public Integer isDefault;
  @Column(name = "created_at")
  public LocalDateTime createdAt;
}

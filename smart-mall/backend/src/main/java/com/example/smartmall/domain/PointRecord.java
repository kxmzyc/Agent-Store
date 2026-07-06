package com.example.smartmall.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "point_record")
public class PointRecord {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;
  @Column(name = "user_id")
  public Long userId;
  public Integer delta;
  public String reason;
  @Column(name = "created_at")
  public LocalDateTime createdAt;
}

package com.example.smartmall.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "admin_operation_log")
public class AdminOperationLog {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;
  @Column(name = "admin_id")
  public Long adminId;
  public String action;
  @Column(name = "target_type")
  public String targetType;
  @Column(name = "target_id")
  public Long targetId;
  public String detail;
  @Column(name = "created_at")
  public LocalDateTime createdAt;
}

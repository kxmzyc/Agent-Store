package com.example.smartmall.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_feedback")
public class UserFeedback {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;
  @Column(name = "user_id")
  public Long userId;
  public Integer type;
  public String content;
  public Integer status;
  public String reply;
  @Column(name = "created_at")
  public LocalDateTime createdAt;
}

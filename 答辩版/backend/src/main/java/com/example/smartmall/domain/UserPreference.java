package com.example.smartmall.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_preference")
public class UserPreference {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;
  @Column(name = "user_id")
  public Long userId;
  @Column(name = "preference_tag")
  public String preferenceTag;
  public BigDecimal weight;
  @Column(name = "updated_at")
  public LocalDateTime updatedAt;
}

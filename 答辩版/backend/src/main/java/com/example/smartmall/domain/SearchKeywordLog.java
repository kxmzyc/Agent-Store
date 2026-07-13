package com.example.smartmall.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "search_keyword_log")
public class SearchKeywordLog {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;
  public String keyword;
  @Column(name = "user_id")
  public Long userId;
  @Column(name = "is_blocked")
  public Integer isBlocked;
  @Column(name = "searched_at")
  public LocalDateTime searchedAt;
}

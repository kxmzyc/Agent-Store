package com.example.smartmall.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "`user`")
public class User {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;
  public String username;
  @Column(name = "password_hash")
  public String passwordHash;
  public String phone;
  @Column(name = "avatar_url")
  public String avatarUrl;
  public String role;
  public Integer status;
  @Column(name = "created_at")
  public LocalDateTime createdAt;
  @Column(name = "updated_at")
  public LocalDateTime updatedAt;
}

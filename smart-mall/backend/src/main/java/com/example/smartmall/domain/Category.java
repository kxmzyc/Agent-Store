package com.example.smartmall.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "category")
public class Category {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;
  public String name;
  @Column(name = "parent_id")
  public Long parentId;
  @Column(name = "sort_order")
  public Integer sortOrder;
}

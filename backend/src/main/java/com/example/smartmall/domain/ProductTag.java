package com.example.smartmall.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "product_tag")
public class ProductTag {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;
  public String name;
}

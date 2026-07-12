package com.example.smartmall.domain;

import jakarta.persistence.*;

@Entity
@IdClass(ProductTagRelationId.class)
@Table(name = "product_tag_relation")
public class ProductTagRelation {
  @Id
  @Column(name = "product_id")
  public Long productId;
  @Id
  @Column(name = "tag_id")
  public Long tagId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tag_id", insertable = false, updatable = false)
  public ProductTag tag;
}

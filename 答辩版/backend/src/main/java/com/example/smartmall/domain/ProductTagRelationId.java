package com.example.smartmall.domain;

import java.io.Serializable;
import java.util.Objects;

public class ProductTagRelationId implements Serializable {
  public Long productId;
  public Long tagId;

  public ProductTagRelationId() {}

  public ProductTagRelationId(Long productId, Long tagId) {
    this.productId = productId;
    this.tagId = tagId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ProductTagRelationId that)) return false;
    return Objects.equals(productId, that.productId) && Objects.equals(tagId, that.tagId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(productId, tagId);
  }
}

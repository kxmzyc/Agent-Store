package com.example.smartmall.repo;

import com.example.smartmall.domain.*;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductTagRelationRepository extends JpaRepository<ProductTagRelation, ProductTagRelationId> {
  @EntityGraph(attributePaths = "tag")
  List<ProductTagRelation> findByProductId(Long productId);

  @EntityGraph(attributePaths = "tag")
  List<ProductTagRelation> findByProductIdIn(List<Long> productIds);

  void deleteByProductId(Long productId);
  void deleteByTagId(Long tagId);
}

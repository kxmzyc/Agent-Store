package com.example.smartmall.repo;

import com.example.smartmall.domain.ProductTag;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductTagRepository extends JpaRepository<ProductTag, Long> {
  List<ProductTag> findAllByOrderByNameAsc();
  boolean existsByName(String name);
}

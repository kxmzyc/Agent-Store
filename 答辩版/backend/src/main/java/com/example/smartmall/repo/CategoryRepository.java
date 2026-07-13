package com.example.smartmall.repo;

import com.example.smartmall.domain.Category;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
  List<Category> findAllByOrderBySortOrderAscIdAsc();
  List<Category> findByParentIdOrderBySortOrderAscIdAsc(Long parentId);
}

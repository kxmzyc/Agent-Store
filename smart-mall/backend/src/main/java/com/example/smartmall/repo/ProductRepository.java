package com.example.smartmall.repo;

import com.example.smartmall.domain.Product;
import java.math.BigDecimal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {
  Page<Product> findByStatus(Integer status, Pageable pageable);
  Page<Product> findByStatusAndCategoryId(Integer status, Long categoryId, Pageable pageable);

  @Query("""
      select p from Product p
      where p.status = 1 and (:keyword is null or lower(p.name) like lower(concat('%', :keyword, '%'))
        or lower(p.description) like lower(concat('%', :keyword, '%')))
      """)
  Page<Product> search(@Param("keyword") String keyword, Pageable pageable);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      update Product p set p.stock = p.stock - :quantity,
        p.version = p.version + 1,
        p.salesCount = p.salesCount + :quantity
      where p.id = :id and p.stock >= :quantity and p.version = :version
      """)
  int deductStock(@Param("id") Long id, @Param("quantity") int quantity, @Param("version") int version);
}

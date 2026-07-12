package com.example.smartmall;

import com.example.smartmall.domain.Product;
import com.example.smartmall.repo.ProductRepository;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class OrderStockSqlTest {
  @Autowired ProductRepository products;

  @Test
  @Transactional
  void deductStockUsesVersionAndStockCondition() {
    Product product = new Product();
    product.categoryId = 1L;
    product.name = "test";
    product.description = "test";
    product.price = BigDecimal.TEN;
    product.stock = 2;
    product.salesCount = 0;
    product.status = 1;
    product.version = 0;
    products.saveAndFlush(product);

    int success = products.deductStock(product.id, 2, 0);
    int staleVersion = products.deductStock(product.id, 1, 0);

    Product reloaded = products.findById(product.id).orElseThrow();
    assertThat(success).isEqualTo(1);
    assertThat(staleVersion).isZero();
    assertThat(reloaded.stock).isZero();
    assertThat(reloaded.version).isEqualTo(1);
  }
}

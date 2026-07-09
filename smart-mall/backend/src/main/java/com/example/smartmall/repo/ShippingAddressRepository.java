package com.example.smartmall.repo;

import com.example.smartmall.domain.ShippingAddress;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ShippingAddressRepository extends JpaRepository<ShippingAddress, Long> {
  List<ShippingAddress> findByUserIdOrderByIsDefaultDescIdDesc(Long userId);
  Optional<ShippingAddress> findByIdAndUserId(Long id, Long userId);
  long countByUserId(Long userId);

  @Modifying
  @Query("update ShippingAddress a set a.isDefault = 0 where a.userId = :userId")
  void clearDefault(@Param("userId") Long userId);
}

package com.example.smartmall.repo;

import com.example.smartmall.domain.AfterSaleRequest;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface AfterSaleRequestRepository extends JpaRepository<AfterSaleRequest, Long> {
  boolean existsByOrderIdAndStatusIn(Long orderId, Collection<Integer> statuses);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select request from AfterSaleRequest request where request.id = :id")
  Optional<AfterSaleRequest> findByIdForUpdate(@Param("id") Long id);
  List<AfterSaleRequest> findByUserIdOrderByCreatedAtDesc(Long userId);
  Page<AfterSaleRequest> findByStatusOrderByCreatedAtDesc(Integer status, Pageable pageable);
  Page<AfterSaleRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);
}

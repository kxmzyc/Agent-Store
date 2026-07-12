package com.example.smartmall.repo;

import com.example.smartmall.domain.AfterSaleRequest;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AfterSaleRequestRepository extends JpaRepository<AfterSaleRequest, Long> {
  boolean existsByOrderIdAndStatusIn(Long orderId, Collection<Integer> statuses);
  List<AfterSaleRequest> findByUserIdOrderByCreatedAtDesc(Long userId);
  Page<AfterSaleRequest> findByStatusOrderByCreatedAtDesc(Integer status, Pageable pageable);
  Page<AfterSaleRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);
}

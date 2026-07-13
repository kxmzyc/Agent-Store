package com.example.smartmall.repo;

import com.example.smartmall.domain.PointRecord;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointRecordRepository extends JpaRepository<PointRecord, Long> {
  List<PointRecord> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
  boolean existsByUserIdAndDeltaAndReason(Long userId, Integer delta, String reason);
  boolean existsByUserIdAndReason(Long userId, String reason);
  List<PointRecord> findByUserIdAndReason(Long userId, String reason);
}

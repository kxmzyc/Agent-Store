package com.example.smartmall.repo;

import com.example.smartmall.domain.UserPreference;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPreferenceRepository extends JpaRepository<UserPreference, Long> {
  List<UserPreference> findByUserIdOrderByWeightDescUpdatedAtDesc(Long userId, Pageable pageable);
}

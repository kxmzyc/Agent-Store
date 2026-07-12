package com.example.smartmall.repo;

import com.example.smartmall.domain.UserFeedback;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserFeedbackRepository extends JpaRepository<UserFeedback, Long> {
  List<UserFeedback> findAllByOrderByCreatedAtDesc();
}

package com.example.smartmall.repo;

import com.example.smartmall.domain.AdminOperationLog;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AdminOperationLogRepository extends JpaRepository<AdminOperationLog, Long> {
  @Query("""
      select l from AdminOperationLog l
      where (:adminId is null or l.adminId = :adminId)
        and (:action is null or :action = '' or lower(l.action) like lower(concat('%', :action, '%')))
      order by l.createdAt desc
      """)
  Page<AdminOperationLog> search(@Param("adminId") Long adminId, @Param("action") String action, Pageable pageable);
}

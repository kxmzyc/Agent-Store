package com.example.smartmall.api;

import com.example.smartmall.repo.AdminOperationLogRepository;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static com.example.smartmall.api.ApiSupport.*;

@RestController
@RequestMapping("/api/admin/operation-logs")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOperationLogController {
  private final AdminOperationLogRepository logs;

  public AdminOperationLogController(AdminOperationLogRepository logs) {
    this.logs = logs;
  }

  @GetMapping
  PageResponse<AdminOperationLogResponse> list(@RequestParam(required = false) Long adminId,
                                               @RequestParam(defaultValue = "") String action,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "20") int size) {
    Pageable pageable = PageRequest.of(Math.max(page, 1) - 1, Math.min(Math.max(size, 1), 100));
    Page<AdminOperationLogResponse> result = logs.search(adminId, action, pageable).map(AdminOperationLogResponse::from);
    return new PageResponse<>(result.getTotalElements(), result.getContent());
  }
}

package com.example.smartmall.audit;

import com.example.smartmall.domain.AdminOperationLog;
import com.example.smartmall.repo.AdminOperationLogRepository;
import com.example.smartmall.security.CurrentUser;
import java.time.LocalDateTime;
import java.util.Arrays;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AdminOperationAspect {
  private final AdminOperationLogRepository logs;

  public AdminOperationAspect(AdminOperationLogRepository logs) {
    this.logs = logs;
  }

  @Around("@annotation(operation)")
  public Object record(ProceedingJoinPoint joinPoint, AdminOperation operation) throws Throwable {
    Object result = joinPoint.proceed();
    CurrentUser admin = currentUser();
    if (admin != null && "ADMIN".equals(admin.role())) {
      AdminOperationLog log = new AdminOperationLog();
      log.adminId = admin.id();
      log.action = operation.action();
      log.targetType = operation.targetType();
      log.targetId = firstLongArg(joinPoint.getArgs());
      log.detail = detail(joinPoint);
      log.createdAt = LocalDateTime.now();
      logs.save(log);
    }
    return result;
  }

  private CurrentUser currentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUser user)) return null;
    return user;
  }

  private Long firstLongArg(Object[] args) {
    return Arrays.stream(args)
        .filter(Long.class::isInstance)
        .map(Long.class::cast)
        .findFirst()
        .orElse(null);
  }

  private String detail(ProceedingJoinPoint joinPoint) {
    String raw = joinPoint.getSignature().toShortString() + " args=" + Arrays.toString(joinPoint.getArgs());
    return raw.length() > 500 ? raw.substring(0, 500) : raw;
  }
}

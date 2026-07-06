package com.example.smartmall.service;

import com.example.smartmall.api.ApiSupport.*;
import com.example.smartmall.api.BizException;
import com.example.smartmall.domain.*;
import com.example.smartmall.repo.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PointService {
  private final UserRepository users;
  private final PointRecordRepository pointRecords;

  public PointService(UserRepository users, PointRecordRepository pointRecords) {
    this.users = users;
    this.pointRecords = pointRecords;
  }

  public PointsResponse myPoints(Long userId) {
    User user = users.findById(userId).orElseThrow(() -> BizException.notFound("用户不存在"));
    return new PointsResponse(
        user.points == null ? 0 : user.points,
        pointRecords.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 30))
            .stream().map(PointRecordResponse::from).toList()
    );
  }

  @Transactional
  public PointDiscount usePoints(Long userId, BigDecimal amount, boolean enabled) {
    if (!enabled) return new PointDiscount(BigDecimal.ZERO, 0);
    User user = users.findById(userId).orElseThrow(() -> BizException.notFound("用户不存在"));
    int points = user.points == null ? 0 : user.points;
    int redeemableYuan = points / 100;
    int maxYuan = amount.subtract(new BigDecimal("0.01")).setScale(0, RoundingMode.DOWN).intValue();
    int yuan = Math.max(0, Math.min(redeemableYuan, maxYuan));
    if (yuan <= 0) return new PointDiscount(BigDecimal.ZERO, 0);
    int pointsUsed = yuan * 100;
    user.points = points - pointsUsed;
    users.save(user);
    addRecord(userId, -pointsUsed, "订单积分抵扣");
    return new PointDiscount(BigDecimal.valueOf(yuan).setScale(2), pointsUsed);
  }

  @Transactional
  public void awardForCompletedOrder(Long userId, BigDecimal paidAmount, String orderNo) {
    int points = paidAmount == null ? 0 : paidAmount.setScale(0, RoundingMode.DOWN).intValue();
    if (points <= 0) return;
    User user = users.findById(userId).orElseThrow(() -> BizException.notFound("用户不存在"));
    user.points = (user.points == null ? 0 : user.points) + points;
    users.save(user);
    addRecord(userId, points, "完成订单 " + orderNo);
  }

  private void addRecord(Long userId, int delta, String reason) {
    PointRecord record = new PointRecord();
    record.userId = userId;
    record.delta = delta;
    record.reason = reason;
    record.createdAt = LocalDateTime.now();
    pointRecords.save(record);
  }

  public record PointDiscount(BigDecimal discountAmount, int pointsUsed) {}
}

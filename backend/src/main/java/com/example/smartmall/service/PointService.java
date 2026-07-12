package com.example.smartmall.service;

import com.example.smartmall.api.ApiSupport.*;
import com.example.smartmall.api.BizException;
import com.example.smartmall.domain.*;
import com.example.smartmall.repo.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PointService {
  private static final Logger log = LoggerFactory.getLogger(PointService.class);
  private static final String COMPLETED_ORDER_REASON_PREFIX = "完成订单 ";
  private static final String REFUND_REVERSAL_REASON_PREFIX = "退款订单 ";
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
    addRecord(userId, points, completedOrderReason(orderNo));
  }

  @Transactional
  public void reverseCompletedOrderAward(Long userId, BigDecimal paidAmount, String orderNo) {
    int awardedPoints = paidAmount == null ? 0 : paidAmount.setScale(0, RoundingMode.DOWN).intValue();
    if (awardedPoints <= 0) return;

    String awardReason = completedOrderReason(orderNo);
    String reversalReason = refundReversalReason(orderNo);
    if (!pointRecords.existsByUserIdAndDeltaAndReason(userId, awardedPoints, awardReason)
        || pointRecords.existsByUserIdAndReason(userId, reversalReason)) {
      return;
    }

    User user = users.findById(userId).orElseThrow(() -> BizException.notFound("用户不存在"));
    int currentPoints = Math.max(user.points == null ? 0 : user.points, 0);
    int reversedPoints = Math.min(awardedPoints, currentPoints);
    user.points = currentPoints - reversedPoints;
    users.save(user);
    addRecord(userId, -reversedPoints, reversalReason);
    log.info("completed order points reversed, userId={}, orderNo={}, awardedPoints={}, reversedPoints={}, unrecoveredPoints={}",
        userId, orderNo, awardedPoints, reversedPoints, awardedPoints - reversedPoints);
  }

  private String completedOrderReason(String orderNo) {
    return COMPLETED_ORDER_REASON_PREFIX + orderNo;
  }

  private String refundReversalReason(String orderNo) {
    return REFUND_REVERSAL_REASON_PREFIX + orderNo + " 积分回滚";
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

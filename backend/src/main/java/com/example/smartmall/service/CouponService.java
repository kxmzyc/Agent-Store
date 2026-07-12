package com.example.smartmall.service;

import com.example.smartmall.api.ApiSupport.*;
import com.example.smartmall.api.BizException;
import com.example.smartmall.domain.*;
import com.example.smartmall.repo.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CouponService {
  private static final BigDecimal MIN_PAYABLE = new BigDecimal("0.01");
  private final CouponRepository coupons;
  private final UserCouponRepository userCoupons;

  public CouponService(CouponRepository coupons, UserCouponRepository userCoupons) {
    this.coupons = coupons;
    this.userCoupons = userCoupons;
  }

  public List<CouponResponse> available(Long userId) {
    Set<Long> claimed = new HashSet<>();
    userCoupons.findByUserIdOrderByCreatedAtDesc(userId).forEach(item -> claimed.add(item.couponId));
    return coupons.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
        .map(coupon -> CouponResponse.from(coupon, claimed.contains(coupon.id)))
        .toList();
  }

  @Transactional
  public UserCouponResponse claim(Long userId, Long couponId) {
    if (userCoupons.existsByUserIdAndCouponId(userId, couponId)) {
      throw BizException.conflict("优惠券已领取");
    }
    Coupon coupon = coupons.findById(couponId).orElseThrow(() -> BizException.notFound("优惠券不存在"));
    if (coupons.claimOne(coupon.id) == 0) {
      throw BizException.badRequest("优惠券已领完");
    }
    UserCoupon userCoupon = new UserCoupon();
    userCoupon.userId = userId;
    userCoupon.couponId = coupon.id;
    userCoupon.status = 0;
    userCoupon.expireAt = LocalDate.now().plusDays(coupon.validDays == null ? 30 : coupon.validDays);
    userCoupon.createdAt = LocalDateTime.now();
    UserCoupon saved = userCoupons.save(userCoupon);
    return UserCouponResponse.from(saved, coupon);
  }

  @Transactional
  public List<UserCouponResponse> myCoupons(Long userId, Integer status) {
    List<UserCoupon> result = status == null
        ? userCoupons.findWithCouponByUserIdOrderByCreatedAtDesc(userId)
        : userCoupons.findWithCouponByUserIdAndStatusOrderByCreatedAtDesc(userId, status);
    LocalDate today = LocalDate.now();
    return result.stream().map(item -> {
      if (item.status != null && item.status == 0 && item.expireAt.isBefore(today)) {
        item.status = 2;
        userCoupons.save(item);
      }
      return UserCouponResponse.from(item);
    }).toList();
  }

  public List<UserCouponResponse> usableCoupons(Long userId) {
    return userCoupons.findUsableWithCoupon(userId, 0, LocalDate.now())
        .stream().map(UserCouponResponse::from).toList();
  }

  public CouponCalculateResponse calculate(Long userId, CouponCalculateRequest request) {
    BigDecimal discount = calculateDiscount(userId, request.userCouponId(), request.orderAmount());
    return new CouponCalculateResponse(request.userCouponId(), money(request.orderAmount()), discount,
        payable(request.orderAmount(), discount));
  }

  @Transactional
  public CouponApplyResult useCoupon(Long userId, Long userCouponId, BigDecimal orderAmount, Long orderId) {
    if (userCouponId == null) {
      return new CouponApplyResult(BigDecimal.ZERO);
    }
    BigDecimal discount = calculateDiscount(userId, userCouponId, orderAmount);
    UserCoupon userCoupon = userCoupons.findWithCouponByIdAndUserId(userCouponId, userId)
        .orElseThrow(() -> BizException.badRequest("优惠券不可用"));
    userCoupon.status = 1;
    userCoupon.usedOrderId = orderId;
    userCoupons.save(userCoupon);
    return new CouponApplyResult(discount);
  }

  public BigDecimal calculateDiscount(Long userId, Long userCouponId, BigDecimal orderAmount) {
    if (userCouponId == null) return BigDecimal.ZERO;
    BigDecimal amount = money(orderAmount);
    UserCoupon userCoupon = userCoupons.findWithCouponByIdAndUserId(userCouponId, userId)
        .orElseThrow(() -> BizException.badRequest("优惠券不可用"));
    if (userCoupon.status == null || userCoupon.status != 0 || userCoupon.expireAt.isBefore(LocalDate.now())) {
      throw BizException.badRequest("优惠券不可用或已过期");
    }
    Coupon coupon = userCoupon.coupon;
    if (coupon == null || coupon.type == null) {
      coupon = coupons.findById(userCoupon.couponId).orElseThrow(() -> BizException.badRequest("优惠券不可用"));
    }
    if (amount.compareTo(money(coupon.threshold)) < 0) {
      throw BizException.badRequest("订单金额未达到优惠券门槛");
    }
    BigDecimal discount = coupon.type != null && coupon.type == 2
        ? amount.subtract(amount.multiply(coupon.discount))
        : coupon.discount;
    BigDecimal maxDiscount = amount.subtract(MIN_PAYABLE);
    if (discount.compareTo(maxDiscount) > 0) {
      discount = maxDiscount;
    }
    return money(discount.max(BigDecimal.ZERO));
  }

  private BigDecimal payable(BigDecimal amount, BigDecimal discount) {
    BigDecimal value = money(amount).subtract(money(discount));
    return value.compareTo(MIN_PAYABLE) < 0 ? MIN_PAYABLE : money(value);
  }

  private BigDecimal money(BigDecimal value) {
    return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
  }

  public record CouponApplyResult(BigDecimal discountAmount) {}
}

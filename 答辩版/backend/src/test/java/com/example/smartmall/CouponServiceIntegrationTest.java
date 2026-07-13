package com.example.smartmall;

import com.example.smartmall.api.ApiSupport.CouponCalculateRequest;
import com.example.smartmall.api.BizException;
import com.example.smartmall.domain.Coupon;
import com.example.smartmall.domain.User;
import com.example.smartmall.domain.UserCoupon;
import com.example.smartmall.repo.CouponRepository;
import com.example.smartmall.repo.UserCouponRepository;
import com.example.smartmall.repo.UserRepository;
import com.example.smartmall.service.CouponService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class CouponServiceIntegrationTest {
  @Autowired CouponService couponService;
  @Autowired UserRepository users;
  @Autowired CouponRepository coupons;
  @Autowired UserCouponRepository userCoupons;
  @Autowired PasswordEncoder passwordEncoder;

  @Test
  void cannotClaimSameCouponTwice() {
    User user = user("claim_user");
    Coupon coupon = coupon("Claim Coupon", "20.00", "5.00");

    couponService.claim(user.id, coupon.id);

    assertThatThrownBy(() -> couponService.claim(user.id, coupon.id))
        .isInstanceOf(BizException.class);
  }

  @Test
  void resetClaimEligibilityAllowsClaimingAgainAndRestoresQuota() {
    User user = user("reset_claim_user");
    Coupon coupon = coupon("Reset Claim Coupon", "20.00", "5.00");

    couponService.claim(user.id, coupon.id);

    assertThat(couponService.resetClaimEligibility(user.id, coupon.id)).isTrue();
    assertThat(userCoupons.findByUserIdAndCouponId(user.id, coupon.id)).isEmpty();
    assertThat(coupons.findById(coupon.id).orElseThrow().remainCount).isEqualTo(100);
    assertThat(couponService.claim(user.id, coupon.id).couponId()).isEqualTo(coupon.id);
  }

  @Test
  void resetClaimEligibilityAllowsClaimingAfterCouponWasUsed() {
    User user = user("used_claim_user");
    Coupon coupon = coupon("Used Claim Coupon", "20.00", "5.00");
    UserCoupon userCoupon = userCoupons.findById(couponService.claim(user.id, coupon.id).id()).orElseThrow();
    userCoupon.status = 1;
    userCoupon.usedOrderId = 1L;
    userCoupons.save(userCoupon);

    assertThat(couponService.resetClaimEligibility(user.id, coupon.id)).isTrue();
    assertThat(userCoupons.findByUserIdAndCouponId(user.id, coupon.id)).isEmpty();
    assertThat(coupons.findById(coupon.id).orElseThrow().remainCount).isEqualTo(100);
    assertThat(couponService.claim(user.id, coupon.id).couponId()).isEqualTo(coupon.id);
  }

  @Test
  void couponBelowThresholdIsRejected() {
    User user = user("threshold_user");
    Coupon coupon = coupon("High Threshold Coupon", "500.00", "50.00");
    UserCoupon userCoupon = new UserCoupon();
    userCoupon.userId = user.id;
    userCoupon.couponId = coupon.id;
    userCoupon.status = 0;
    userCoupon.expireAt = LocalDate.now().plusDays(30);
    userCoupon.createdAt = LocalDateTime.now();
    userCoupons.save(userCoupon);

    assertThatThrownBy(() ->
        couponService.calculate(user.id, new CouponCalculateRequest(userCoupon.id, new BigDecimal("100.00"))))
        .isInstanceOf(BizException.class);
  }

  private User user(String prefix) {
    User user = new User();
    user.username = prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    user.passwordHash = passwordEncoder.encode("secret123");
    user.phone = "136" + String.format("%08d", Math.floorMod(user.username.hashCode(), 100_000_000));
    user.role = "USER";
    user.status = 1;
    user.points = 0;
    user.createdAt = LocalDateTime.now();
    user.updatedAt = user.createdAt;
    return users.save(user);
  }

  private Coupon coupon(String name, String threshold, String discount) {
    Coupon coupon = new Coupon();
    coupon.name = name + " " + UUID.randomUUID().toString().substring(0, 8);
    coupon.type = 1;
    coupon.threshold = new BigDecimal(threshold);
    coupon.discount = new BigDecimal(discount);
    coupon.totalCount = 100;
    coupon.remainCount = 100;
    coupon.validDays = 30;
    coupon.createdAt = LocalDateTime.now();
    return coupons.save(coupon);
  }
}

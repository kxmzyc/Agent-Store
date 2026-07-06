package com.example.smartmall.api;

import com.example.smartmall.security.CurrentUser;
import com.example.smartmall.service.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import static com.example.smartmall.api.ApiSupport.*;

@RestController
@RequestMapping("/api")
public class CouponController {
  private final CouponService couponService;
  private final PointService pointService;

  public CouponController(CouponService couponService, PointService pointService) {
    this.couponService = couponService;
    this.pointService = pointService;
  }

  @GetMapping("/coupons/available")
  List<CouponResponse> available(@AuthenticationPrincipal CurrentUser user) {
    return couponService.available(user.id());
  }

  @PostMapping("/coupons/{couponId}/claim")
  ResponseEntity<UserCouponResponse> claim(@AuthenticationPrincipal CurrentUser user, @PathVariable Long couponId) {
    return ResponseEntity.status(HttpStatus.CREATED).body(couponService.claim(user.id(), couponId));
  }

  @GetMapping("/coupons/my")
  List<UserCouponResponse> myCoupons(@AuthenticationPrincipal CurrentUser user,
                                     @RequestParam(required = false) Integer status) {
    return couponService.myCoupons(user.id(), status);
  }

  @PostMapping("/coupons/calculate")
  CouponCalculateResponse calculate(@AuthenticationPrincipal CurrentUser user,
                                    @Valid @RequestBody CouponCalculateRequest request) {
    return couponService.calculate(user.id(), request);
  }

  @GetMapping("/points/my")
  PointsResponse myPoints(@AuthenticationPrincipal CurrentUser user) {
    return pointService.myPoints(user.id());
  }
}

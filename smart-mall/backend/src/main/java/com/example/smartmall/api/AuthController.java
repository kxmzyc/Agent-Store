package com.example.smartmall.api;

import com.example.smartmall.domain.User;
import com.example.smartmall.repo.UserRepository;
import com.example.smartmall.security.CurrentUser;
import com.example.smartmall.security.JwtService;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import static com.example.smartmall.api.ApiSupport.*;

@RestController
@RequestMapping("/api")
public class AuthController {
  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;

  public AuthController(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
  }

  @PostMapping("/auth/register")
  ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
    if (users.existsByUsername(request.username())) {
      throw BizException.conflict("用户名已存在");
    }
    if (request.phone() != null && !request.phone().isBlank() && users.existsByPhone(request.phone())) {
      throw BizException.conflict("手机号已存在");
    }
    User user = new User();
    user.username = request.username();
    user.passwordHash = passwordEncoder.encode(request.password());
    user.phone = request.phone();
    user.role = "USER";
    user.status = 1;
    user.points = 0;
    user.createdAt = LocalDateTime.now();
    user.updatedAt = user.createdAt;
    users.save(user);
    return ResponseEntity.status(HttpStatus.CREATED).body(new RegisterResponse(user.id, user.username));
  }

  @PostMapping("/auth/login")
  LoginResponse login(@Valid @RequestBody LoginRequest request) {
    User user = users.findByUsername(request.username())
        .filter(u -> u.status != null && u.status == 1)
        .filter(u -> passwordEncoder.matches(request.password(), u.passwordHash))
        .orElseThrow(() -> BizException.unauthorized("用户名或密码错误"));
    return new LoginResponse(
        jwtService.accessToken(user.id, user.username, user.role),
        jwtService.refreshToken(user.id, user.username, user.role),
        UserResponse.from(user));
  }

  @PostMapping("/auth/refresh")
  TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
    var claims = jwtService.parse(request.refreshToken()).getPayload();
    if (!"refresh".equals(claims.get("type", String.class))) {
      throw BizException.unauthorized("Refresh Token无效");
    }
    Long userId = Long.valueOf(claims.getSubject());
    User user = users.findById(userId).orElseThrow(() -> BizException.unauthorized("Refresh Token无效"));
    return new TokenResponse(jwtService.accessToken(user.id, user.username, user.role));
  }

  @GetMapping("/user/profile")
  UserResponse profile(@AuthenticationPrincipal CurrentUser currentUser) {
    return UserResponse.from(users.findById(currentUser.id()).orElseThrow(() -> BizException.notFound("用户不存在")));
  }

  @PutMapping("/user/profile")
  UserResponse updateProfile(@AuthenticationPrincipal CurrentUser currentUser, @RequestBody ProfileUpdateRequest request) {
    User user = users.findById(currentUser.id()).orElseThrow(() -> BizException.notFound("用户不存在"));
    if (request.phone() != null) {
      if (!request.phone().equals(user.phone) && users.existsByPhone(request.phone())) {
        throw BizException.conflict("手机号已存在");
      }
      user.phone = request.phone();
    }
    if (request.avatarUrl() != null) {
      user.avatarUrl = request.avatarUrl();
    }
    user.updatedAt = LocalDateTime.now();
    return UserResponse.from(users.save(user));
  }
}

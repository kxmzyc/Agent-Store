package com.example.smartmall.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
  private final SecretKey key;

  public JwtService(@Value("${app.jwt-secret}") String secret) {
    if (secret == null || secret.length() < 32) {
      throw new IllegalStateException("JWT_SECRET must contain at least 32 characters");
    }
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }

  public String accessToken(Long userId, String username, String role) {
    return token(userId, username, role, "access", 2 * 60 * 60);
  }

  public String refreshToken(Long userId, String username, String role) {
    return token(userId, username, role, "refresh", 7 * 24 * 60 * 60);
  }

  public Jws<Claims> parse(String token) {
    return Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
  }

  private String token(Long userId, String username, String role, String type, long seconds) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(String.valueOf(userId))
        .claim("username", username)
        .claim("role", role)
        .claim("type", type)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(seconds)))
        .signWith(key)
        .compact();
  }
}

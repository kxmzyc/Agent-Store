package com.example.smartmall;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordEncoderTest {
  @Test
  void bcryptPasswordMatchesPlainPassword() {
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    String encoded = encoder.encode("123456");

    assertThat(encoded).startsWith("$2a$10$");
    assertThat(encoder.matches("123456", encoded)).isTrue();
    assertThat(encoder.matches("wrong", encoded)).isFalse();
  }
}

package com.example.smartmall.api;

import org.springframework.http.HttpStatus;

public class BizException extends RuntimeException {
  public final HttpStatus status;

  public BizException(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }

  public static BizException badRequest(String message) {
    return new BizException(HttpStatus.BAD_REQUEST, message);
  }

  public static BizException unauthorized(String message) {
    return new BizException(HttpStatus.UNAUTHORIZED, message);
  }

  public static BizException conflict(String message) {
    return new BizException(HttpStatus.CONFLICT, message);
  }

  public static BizException notFound(String message) {
    return new BizException(HttpStatus.NOT_FOUND, message);
  }
}

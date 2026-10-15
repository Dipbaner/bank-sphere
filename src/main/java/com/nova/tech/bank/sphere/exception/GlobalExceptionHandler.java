package com.nova.tech.bank.sphere.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> fields.put(e.getField(), e.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid input", fields);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicate(DuplicateResourceException ex) {
        return build(HttpStatus.CONFLICT, "DUPLICATE", ex.getMessage(), null);
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus s, String code, String msg, Object details) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", s.value());
        body.put("code", code);
        body.put("message", msg);
        if (details != null) body.put("details", details);
        return ResponseEntity.status(s).body(body);
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidToken(InvalidTokenException ex) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_TOKEN", ex.getMessage(), null);
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<Map<String, Object>> handleAuthFailed(AuthenticationFailedException ex) {
        return build(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", ex.getMessage(), null);
    }

    @ExceptionHandler(AccountStateException.class)
    public ResponseEntity<Map<String, Object>> handleAccountState(AccountStateException ex) {
        return build(HttpStatus.FORBIDDEN, ex.getCode(), ex.getMessage(), null);
    }

    @ExceptionHandler(InvalidOtpException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidOtp(InvalidOtpException ex) {
        return build(HttpStatus.UNAUTHORIZED, "INVALID_OTP", ex.getMessage(), null);
    }

    @ExceptionHandler(OtpException.class)
    public ResponseEntity<Map<String, Object>> handleOtp(OtpException ex) {
        return build(HttpStatus.CONFLICT, ex.getCode(), ex.getMessage(), null);
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidRefresh(InvalidRefreshTokenException ex) {
        return build(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", ex.getMessage(), null);
    }
}

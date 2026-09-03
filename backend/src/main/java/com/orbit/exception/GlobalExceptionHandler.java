package com.orbit.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<?> forbidden(Exception e) {
        log.warn("Access denied: {}", e.getMessage());
        return ResponseEntity.status(403).body(Map.of("error", "Forbidden", "message", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> general(Exception e) {
        log.error("Unhandled exception: {}", e.getMessage(), e);
        return ResponseEntity.status(500).body(Map.of("error", "Internal error", "message", e.getMessage()));
    }
}

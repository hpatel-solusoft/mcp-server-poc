package com.solusoft.ai.mcp.exception;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice(basePackages = "com.solusoft.ai.mcp")
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(Case360IntegrationException.class)
    @RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE) 
    public ResponseEntity<Map<String, Object>> handleIntegrationError(Case360IntegrationException ex) {
        log.error("Legacy System Failure: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_GATEWAY, "CASE360_ERROR", "Failed to communicate with legacy system.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    @RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE) 
    public ResponseEntity<Map<String, Object>> handleAuthError(AccessDeniedException ex) {
        log.warn("Security Alert: Unauthorized access attempt.");
        return buildResponse(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "You do not have permission to perform this action.");
    }
    
    @ExceptionHandler(IllegalArgumentException.class)
    @RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE) 
    public ResponseEntity<Map<String, Object>> handleBadInput(IllegalArgumentException ex) {
        log.warn("Validation Error: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "INVALID_INPUT", ex.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE) 
    public ResponseEntity<Map<String, Object>> handle404(NoResourceFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, "NOT_FOUND", "Endpoint does not exist.");
    }

    @ExceptionHandler(Exception.class)
    @RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE) 
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        // Log the full stack trace for generic errors
        log.error("Unhandled System Exception", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected system error occurred.");
    }
    
    @ExceptionHandler(CallNotPermittedException.class)
    @RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE) 
    public ResponseEntity<Map<String, Object>> handleCircuitBreakerOpen(CallNotPermittedException ex) {
    	
    	log.error("Error in establishing connection", ex);
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", "Service is temporarily offline for maintenance.");
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String code, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error_code", code);
        body.put("message", message);
        body.put("trace_id", MDC.get("trace_id")); // Include the ID for support tickets

        return ResponseEntity.status(status).body(body);
    }
}
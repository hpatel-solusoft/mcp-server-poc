package com.solusoft.ai.mcp.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Aspect
@Component
@Slf4j
public class McpAuditAspect {

    // Intercept ANY method annotated with @McpTool
    @Around("@annotation(org.springaicommunity.mcp.annotation.McpTool)")
    public Object auditToolCall(ProceedingJoinPoint joinPoint) throws Throwable {
        
        // 1. Who is calling?
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String user = (auth != null) ? auth.getName() : "Anonymous";
        String authorities = (auth != null) ? auth.getAuthorities().toString() : "[]";
        
        // 2. What are they calling?
        String methodName = joinPoint.getSignature().getName();
        
        log.info("🕵️ [AUDIT START] User='{}' Role={} Action='{}'", user, authorities, methodName);
        
        Instant start = Instant.now();
        boolean success = true;
        String errorMessage = null;

        try {
            // 3. Run the actual tool
            return joinPoint.proceed();
        } catch (Throwable ex) {
            success = false;
            errorMessage = ex.getMessage();
            throw ex; // Re-throw so the error is still returned to the AI
        } finally {
            // 4. Log the Result
            long timeTaken = Duration.between(start, Instant.now()).toMillis();
            if (success) {
                log.info("✅ [AUDIT SUCCESS] User='{}' Action='{}' Time={}ms", user, methodName, timeTaken);
            } else {
                log.error("❌ [AUDIT FAILURE] User='{}' Action='{}' Error='{}'", user, methodName, errorMessage);
            }
        }
    }
}
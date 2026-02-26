package com.solusoft.ai.mcp.integration.case360;

import java.math.BigDecimal;

import org.jobrunr.scheduling.JobScheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.solusoft.ai.mcp.exception.Case360IntegrationException;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class Case360RecoveryHandler {

    private final JobScheduler jobScheduler;
    private final Case360CleanupService cleanupService;
    private final JdbcTemplate jdbcTemplate;
    
    public Case360RecoveryHandler(JobScheduler jobScheduler, Case360CleanupService cleanupService, JdbcTemplate jdbcTemplate) {
		this.jobScheduler = jobScheduler;
		this.cleanupService = cleanupService;
		this.jdbcTemplate = jdbcTemplate;
	}
	
    public String handleStringFailure(Throwable t, String context) {
        log.error("Circuit Breaker Triggered [{}]: {}", context, t.getMessage());
        return "ERROR_SERVICE_UNAVAILABLE: The legacy system is currently unresponsive. Action: " + context;
    }

    public BigDecimal handleBigDecimalFailure(Throwable t, String context) {
        log.error("Circuit Breaker Triggered [{}]: {}", context, t.getMessage());
        return BigDecimal.ZERO; 
    }

    public boolean handlePingFallback(Throwable t) {
        log.warn("Health check bypassed via Resilience: Case360 is down.");
        return false;
    }

    public void handleVoidFailure(Throwable t, String context) {
        log.error("Circuit Breaker Triggered (Void) [{}]: {}", context, t.getMessage());
        // In a production app, you might queue this action for later retry
    }
    public <T> T handleCriticalFailure(Throwable t, String context) {
        log.error("[CRITICAL] Operation '{}' failed. Circuit may be OPEN. Error: {}", context, t.getMessage());
        throw new Case360IntegrationException("Legacy Case360 system is currently unavailable or timed out during: " + context, t);
    }
    
    public void registerForAsyncCleanup(String instanceId, Integer repositoryId, String reason) {
        log.warn("Registering Ghost Case for Async Cleanup: {}", instanceId);
        
        if (instanceId == null || instanceId.trim().isEmpty()) {
            log.debug("Skipping cleanup registration: No ID was generated yet.");
            return;
        }
        // 1. Log to your CUSTOM Audit Table
        jdbcTemplate.update(
            "INSERT INTO pending_cleanups (instance_id, repository_id, failure_reason) VALUES (?, ?, ?)",
            instanceId, repositoryId, reason
        );

        // 2. ENQUEUE the modern background job
        // This returns immediately. The work happens in another thread.
        jobScheduler.enqueue(() -> cleanupService.performLegacyCleanup(instanceId, repositoryId));
    }
}
package com.solusoft.ai.mcp.integration.case360;

import org.jobrunr.jobs.annotations.Job;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class Case360CleanupService {

    private final Case360SoapTemplate soapTemplate; // MOVED TO TEMPLATE
    private final JdbcTemplate jdbcTemplate;

    public Case360CleanupService(Case360SoapTemplate soapTemplate, JdbcTemplate jdbcTemplate) {
        this.soapTemplate = soapTemplate;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Job(name = "Case360 Orphan Cleanup", retries = 10)
    @Transactional
    public void performLegacyCleanup(String instanceId, Integer repositoryId) {
        log.info("Entering performLegacyCleanup");
        log.info("[CLEANUP] Starting background task for {}: {}", repositoryId, instanceId);
        
        updateCleanupStatus(instanceId, "IN_PROGRESS");

        try {
            // Use Template for raw execution to break the circular loop
            if (repositoryId == 1) {
                soapTemplate.executeRemoveCaseFolder(instanceId);
            } else if (repositoryId == 3) {
                soapTemplate.executeRemoveFileStore(instanceId);
            }

            jdbcTemplate.update(
                "UPDATE pending_cleanups SET status = 'COMPLETED', retry_count = retry_count + 1 WHERE instance_id = ?", 
                instanceId
            );
            log.info("[CLEANUP] Successfully removed ghost {}: {}", repositoryId, instanceId);

        } catch (Exception e) {
            jdbcTemplate.update(
                "UPDATE pending_cleanups SET retry_count = retry_count + 1, failure_reason = ? WHERE instance_id = ?", 
                e.getMessage(), instanceId
            );
            log.error("❌ [CLEANUP] Failed for {}. JobRunr will retry automatically.", instanceId);
            throw e; 
        }
    }

    private void updateCleanupStatus(String instanceId, String status) {
        jdbcTemplate.update("UPDATE pending_cleanups SET status = ? WHERE instance_id = ?", status, instanceId);
    }
}
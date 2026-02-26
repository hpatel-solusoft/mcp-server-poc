package com.solusoft.ai.mcp.config;

import org.jobrunr.jobs.mappers.JobMapper;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.storage.InMemoryStorageProvider;
import org.jobrunr.storage.StorageProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import static org.mockito.Mockito.mock;

@TestConfiguration
public class TestJobRunrConfig {

    @Bean
    @Primary
    public StorageProvider storageProvider(JobMapper jobMapper) {
        // This is usually in the core jobrunr artifact and should resolve
        InMemoryStorageProvider storageProvider = new InMemoryStorageProvider();
        storageProvider.setJobMapper(jobMapper);
        return storageProvider;
    }

    @Bean
    @Primary
    public JobMapper jobMapper() {
        // Use a Mock to bypass all Jackson/JSON import issues
        // This is perfectly fine for unit/integration tests that don't execute background jobs
        return mock(JobMapper.class);
    }
    
    @Bean
    @Primary
    public JobScheduler jobScheduler() {
        // This satisfies the dependency for ClaimPersistenceService and Case360RecoveryHandler
        return mock(JobScheduler.class);
    }
}
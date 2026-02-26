package com.solusoft.ai.mcp.config;

import javax.sql.DataSource;
import org.jobrunr.jobs.mappers.JobMapper;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.storage.sql.postgres.PostgresStorageProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test") // This bean will only exist if the 'test' profile is NOT active
public class JobRunrConfig {

    /**
     * Explicitly define the StorageProvider using your existing DataSource.
     * This resolves the 'No qualifying bean' error by manually bridging 
     * JobRunr to your PostgreSQL instance.
     */
    @Bean
    public StorageProvider storageProvider(DataSource dataSource, JobMapper jobMapper) {
        StorageProvider storageProvider = new PostgresStorageProvider(dataSource);
        storageProvider.setJobMapper(jobMapper);
        return storageProvider;
    }
}
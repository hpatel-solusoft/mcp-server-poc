package com.solusoft.ai.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import org.jobrunr.scheduling.JobScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.solusoft.ai.mcp.config.TestJobRunrConfig;
import com.solusoft.ai.mcp.features.claims.repository.ClaimRepository;
import com.solusoft.ai.mcp.integration.case360.Case360Client;

@SpringBootTest
@Import(TestJobRunrConfig.class)
class ClaimsMcpServerApplicationTests extends AbstractBaseTest {

    @Autowired
    private Case360Client case360Client;
    
    @Autowired
    private ClaimRepository claimRepository;

    @MockitoBean
    private JobScheduler jobScheduler; // This satisfies ClaimPersistenceService

    @Test
    void contextLoads() {
        // This assertion proves that:
        // 1. Postgres was successfully swapped for H2 (otherwise startup fails)
        // 2. Vault was successfully disabled (otherwise startup fails)
        assertThat(case360Client).isNotNull();
        assertThat(claimRepository).isNotNull();
        assertThat(jobScheduler).isNotNull();
    }
}
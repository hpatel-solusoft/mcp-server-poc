package com.solusoft.ai.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.solusoft.ai.mcp.features.claims.repository.ClaimRepository;
import com.solusoft.ai.mcp.integration.case360.Case360Client;

@SpringBootTest
class ClaimsMcpServerApplicationTests extends AbstractBaseTest {

    @Autowired
    private Case360Client case360Client;
    
    @Autowired
    private ClaimRepository claimRepository;

    @Test
    void contextLoads() {
        // This assertion proves that:
        // 1. Postgres was successfully swapped for H2 (otherwise startup fails)
        // 2. Vault was successfully disabled (otherwise startup fails)
        assertThat(case360Client).isNotNull();
        assertThat(claimRepository).isNotNull();
    }
}
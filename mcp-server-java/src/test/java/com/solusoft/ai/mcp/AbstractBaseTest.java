package com.solusoft.ai.mcp;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.solusoft.ai.mcp.security.controller.AdminKeyController;
import com.solusoft.ai.mcp.security.service.ApiKeyService;

@SpringBootTest(classes = McpServerApplication.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
    // 1. Network Isolation
    "spring.cloud.vault.enabled=false",
    "spring.cloud.vault.authentication=TOKEN",
    "spring.cloud.vault.token=dummy-token",
    "spring.config.import=optional:vault://",
    
    // 2. Database Isolation (H2)
    "spring.flyway.enabled=false", // We use schema.sql instead
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "spring.datasource.driver-class-name=org.h2.Driver",

    // 3. Mock SOAP Config
    "case360.url=http://localhost:8080/sonora/soap/Ws",
    "case360.username=test-user",
    "case360.password=test-pass",
    "case360.timeout.connect=1000",
    "case360.timeout.read=1000",
    "case360.pool.max-total=10",
    "case360.pool.max-per-route=5",
    "case360.pool.ttl-minutes=5"
})
public abstract class AbstractBaseTest {
	@MockitoBean
    protected ApiKeyService apiKeyService;
	@MockitoBean
	private AdminKeyController adminKeyController;
}
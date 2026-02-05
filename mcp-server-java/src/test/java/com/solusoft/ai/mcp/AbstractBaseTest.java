package com.solusoft.ai.mcp;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Modern Base Test Class.
 * Holds all shared configuration so we don't repeat it in every file.
 */
@SpringBootTest(classes = McpServerApplication.class)
@TestPropertySource(properties = {
    // 1. Disable Vault & Real DB
    "spring.cloud.vault.enabled=false",
    "spring.cloud.vault.authentication=TOKEN",
    "spring.cloud.vault.token=dummy-token",
    "spring.config.import=optional:vault://",
    
    // 2. Use H2 In-Memory DB
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.flyway.enabled=false",

    // 3. Mock SOAP Config (Required for Context Load)
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
    // Shared setup logic can go here in the future
}
package com.solusoft.ai.mcp.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import com.solusoft.ai.mcp.integration.case360.Case360Client;

@Component
public class Case360HealthIndicator implements HealthIndicator {

    private final Case360Client client;

    public Case360HealthIndicator(Case360Client client) {
        this.client = client;
    }

    @Override
    public Health health() {
        try {
        	boolean up = client.ping();
            
            if (up) {
                return Health.up()
                    .withDetail("system", "Case360")
                    .withDetail("latency", "Low") // You could measure time taken in ping()
                    .build();
            } else {
                return Health.down()
                    .withDetail("system", "Case360")
                    .withDetail("error", "Connection Refused or Timeout")
                    .build();
            }
        } catch (Exception e) {
            return Health.down()
                .withDetail("system", "Case360")
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
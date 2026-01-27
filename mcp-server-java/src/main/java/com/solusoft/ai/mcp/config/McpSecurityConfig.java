package com.solusoft.ai.mcp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.solusoft.ai.mcp.security.McpHeaderAuthenticationFilter;
import com.solusoft.ai.mcp.security.service.ApiKeyService;

@Configuration
@EnableMethodSecurity(securedEnabled = true) // Enables @PreAuthorize
public class McpSecurityConfig {

	private final ApiKeyService apiKeyService;

    // Remove McpAuthProperties, inject Service instead
    public McpSecurityConfig(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
            	.requestMatchers("/actuator/**").permitAll()
            	.requestMatchers("/admin/**").permitAll() 
                .requestMatchers("/mcp/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(
                new McpHeaderAuthenticationFilter(apiKeyService), 
                UsernamePasswordAuthenticationFilter.class
            );
        return http.build();
    }
}
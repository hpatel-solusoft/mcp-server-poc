package com.solusoft.ai.mcp.config;

import com.solusoft.ai.mcp.security.McpHeaderAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class McpSecurityConfig {

    // 1. Spring injects the value here from application.properties / Env Vars
    @Value("${mcp.security.api-key}")
    private String mcpApiKey;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF for non-browser APIs
            .csrf(csrf -> csrf.disable())
            
            // Define URL authorization rules
            .authorizeHttpRequests(auth -> auth
                 // We allow "permitAll" because our Custom Filter handles the blocking.
                 // This avoids Spring demanding a full User Login session.
                .requestMatchers("/mcp/**").permitAll()
                .anyRequest().authenticated()
            )
            
            // Register our standalone filter
            // We pass 'mcpApiKey' into the constructor manually here.
            .addFilterBefore(
                new McpHeaderAuthenticationFilter(mcpApiKey), 
                UsernamePasswordAuthenticationFilter.class
            );
            
        return http.build();
    }
}
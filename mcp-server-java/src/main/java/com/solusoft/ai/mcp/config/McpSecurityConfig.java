package com.solusoft.ai.mcp.config;

import com.solusoft.ai.mcp.security.McpAuthProperties;
import com.solusoft.ai.mcp.security.McpHeaderAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableMethodSecurity(securedEnabled = true) // Enables @PreAuthorize
public class McpSecurityConfig {

    private final McpAuthProperties authProperties;

    public McpSecurityConfig(McpAuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        
        // 1. Build the Dynamic Security Map (Key -> Role)
        Map<String, String> runtimeRoleMap = new HashMap<>();
        if (authProperties.getUsers() != null) {
            authProperties.getUsers().forEach((name, user) -> {
                if (user.getKey() != null && user.getRole() != null) {
                    runtimeRoleMap.put(user.getKey(), user.getRole());
                    // Log safely (masking the key)
                    System.out.println("[SEC] Registered Principal: " + name + " -> " + user.getRole());
                }
            });
        }

        // 2. Configure HTTP Security
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/mcp/**").permitAll() // Auth handled by filter
                .requestMatchers("/actuator/**").permitAll() // Warning: Secure this later
                .anyRequest().authenticated()
            )
            // 3. Register the Filter with the Map
            .addFilterBefore(
                new McpHeaderAuthenticationFilter(runtimeRoleMap), 
                UsernamePasswordAuthenticationFilter.class
            );
            
        return http.build();
    }
}
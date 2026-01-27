package com.solusoft.ai.mcp.security;

import java.io.IOException;
import java.util.Collections;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.util.StringUtils; // Import Spring utility

import com.solusoft.ai.mcp.features.claims.model.ApiKeyEntity;
import com.solusoft.ai.mcp.security.service.ApiKeyService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class McpHeaderAuthenticationFilter extends OncePerRequestFilter {

    private final ApiKeyService apiKeyService;

    public McpHeaderAuthenticationFilter(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. Extract Header
        String clientKey = request.getHeader("X-MCP-API-KEY");

        // 2. Logic: Only attempt auth if the header is present. 
        // If missing, we continue. Spring Security will block it later if the path requires auth.
        if (StringUtils.hasText(clientKey)) {
            try {
                // 3. Validate (See performance note below regarding caching)
                ApiKeyEntity identity = apiKeyService.validateKey(clientKey);

                if (identity != null) {
                    // 4. Success: Populate Context
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        "McpAgent", null, 
                        Collections.singletonList(new SimpleGrantedAuthority(identity.getRole()))
                    );
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } 
                // Note: If identity is null (invalid key), we simply do NOT set the context.
                // The request remains "Anonymous".
            } catch (Exception e) {
                // Log error, but do not throw. 
                // Ensure context is clear so no accidental access occurs.
                SecurityContextHolder.clearContext(); 
            }
        }

        // 5. Always continue the chain
        filterChain.doFilter(request, response);
    }
}
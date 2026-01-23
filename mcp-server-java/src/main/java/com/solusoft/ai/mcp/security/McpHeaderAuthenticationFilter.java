package com.solusoft.ai.mcp.security;

import java.io.IOException;
import java.util.Collections;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

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

        if (request.getRequestURI().startsWith("/mcp")) {
            String clientKey = request.getHeader("X-MCP-API-KEY");

            if (clientKey != null) {
                // DB Lookup happens here
            	ApiKeyEntity identity = apiKeyService.validateKey(clientKey);

                if (identity != null) {
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        "McpAgent", null, 
                        Collections.singletonList(new SimpleGrantedAuthority(identity.getRole()))
                    );
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } else {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }
            } else {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
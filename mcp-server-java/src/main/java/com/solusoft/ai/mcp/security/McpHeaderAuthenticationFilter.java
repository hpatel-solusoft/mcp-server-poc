package com.solusoft.ai.mcp.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/**
 * A dedicated filter to validate the MCP API Key.
 * logic is isolated here for easier testing and maintenance.
 */
public class McpHeaderAuthenticationFilter extends OncePerRequestFilter {

    private final String validApiKey;

    // We force the dependency via Constructor. 
    // This makes it impossible to create this filter without a key.
    public McpHeaderAuthenticationFilter(String validApiKey) {
        this.validApiKey = validApiKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // 1. Target only MCP endpoints
        if (path.startsWith("/mcp")) {
            String clientKey = request.getHeader("X-MCP-API-KEY");

            // 2. Validate
            if (clientKey == null || !clientKey.equals(validApiKey)) {
                // 3. Reject immediately
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("Unauthorized: Invalid or Missing MCP API Key");
                return; // Break the chain
            }
        }

        // 4. If valid (or not an MCP path), proceed
        filterChain.doFilter(request, response);
    }
}
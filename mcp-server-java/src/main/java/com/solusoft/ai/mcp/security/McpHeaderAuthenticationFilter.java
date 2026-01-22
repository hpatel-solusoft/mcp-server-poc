package com.solusoft.ai.mcp.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

public class McpHeaderAuthenticationFilter extends OncePerRequestFilter {

    private final Map<String, String> keyRoleMap;

    public McpHeaderAuthenticationFilter(Map<String, String> keyRoleMap) {
        this.keyRoleMap = keyRoleMap;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (request.getRequestURI().startsWith("/mcp")) {
            String clientKey = request.getHeader("X-MCP-API-KEY");
            if (clientKey != null && keyRoleMap.containsKey(clientKey)) {
                String role = keyRoleMap.get(clientKey);
                
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    "McpAgent", 
                    null, 
                    Collections.singletonList(new SimpleGrantedAuthority(role))
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
            } else {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("Unauthorized: Invalid or Missing API Key");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
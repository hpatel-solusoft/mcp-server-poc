package com.solusoft.ai.mcp.config;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Assigns a unique ID to every request and places it in the logging context (MDC).
 * This ensures every log line generated during this request has the same "trace_id".
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    private static final String MDC_KEY = "trace_id";
    private static final String HEADER_KEY = "X-Request-ID";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            // 1. Check if client sent an ID, otherwise generate one
            String requestId = request.getHeader(HEADER_KEY);
            if (requestId == null || requestId.isEmpty()) {
                requestId = UUID.randomUUID().toString();
            }

            // 2. Put in MDC (Logging Context)
            MDC.put(MDC_KEY, requestId);

            // 3. Return it to the client in the response header
            response.setHeader(HEADER_KEY, requestId);

            filterChain.doFilter(request, response);

        } finally {
            // 4. CLEANUP (Crucial for Thread Pooling)
            MDC.remove(MDC_KEY);
        }
    }
}
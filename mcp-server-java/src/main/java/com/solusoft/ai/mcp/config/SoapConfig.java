package com.solusoft.ai.mcp.config;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.transport.http.HttpComponents5MessageSender;

@Configuration
public class SoapConfig {

    @Value("${case360.url}") private String case360Url;
    @Value("${case360.username}") private String username;
    @Value("${case360.password}") private String password;
    
    @Value("${case360.timeout.connect:5000}") private int connectTimeout;
    @Value("${case360.timeout.read:30000}") private int readTimeout;
    
    @Value("${case360.pool.max-total:50}") private int maxTotal;
    @Value("${case360.pool.max-per-route:20}") private int maxPerRoute;
    @Value("${case360.pool.ttl-minutes:10}") private int ttlMinutes;

    @Bean
    public Jaxb2Marshaller marshaller() {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        // MUST match the package where your WSDL classes were generated
        marshaller.setContextPath("com.solusoft.ai.mcp.integration.case360.soap");
        return marshaller;
    }

    /**
     * BEAN 1: Connection Manager (Infrastructure)
     * Handles pooling, physical TCP connections, and strict concurrency rules.
     */
    @Bean
    public PoolingHttpClientConnectionManager case360ConnectionManager() {
        // TCP Socket Level Config
        SocketConfig socketConfig = SocketConfig.custom()
                .setSoTimeout(Timeout.of(readTimeout, TimeUnit.MILLISECONDS))
                .build();

        // High-Level Connection Config
        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.of(connectTimeout, TimeUnit.MILLISECONDS))
                .setSocketTimeout(Timeout.of(readTimeout, TimeUnit.MILLISECONDS))
                .setTimeToLive(TimeValue.ofMinutes(ttlMinutes)) // Prevent stale connections
                .build();

        return PoolingHttpClientConnectionManagerBuilder.create()
                .setDefaultSocketConfig(socketConfig)
                .setDefaultConnectionConfig(connectionConfig)
                .setPoolConcurrencyPolicy(PoolConcurrencyPolicy.STRICT) // FIFO queue for threads
                .setMaxConnTotal(maxTotal)
                .setMaxConnPerRoute(maxPerRoute)
                .build();
    }

    /**
     * BEAN 2: HTTP Client (Transport)
     * Handles Authentication, Headers, and Idle Connection Eviction.
     */
    @Bean
    public CloseableHttpClient case360HttpClient(PoolingHttpClientConnectionManager manager) {
        RequestConfig requestConfig = RequestConfig.custom()
                .setResponseTimeout(Timeout.of(readTimeout, TimeUnit.MILLISECONDS))
                .build();

        // Optimization: Compute Auth header once at startup
        String auth = username + ":" + password;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        final String authHeader = "Basic " + encodedAuth;

        return HttpClients.custom()
                .setConnectionManager(manager)
                .setDefaultRequestConfig(requestConfig)
                // Critical for Long-Running Apps: Clean up connections that sit idle too long
                .evictExpiredConnections()
                .evictIdleConnections(TimeValue.ofMinutes(1)) 
                
                // Interceptor 1: Pre-emptive Auth
                .addRequestInterceptorFirst((request, entity, context) -> {
                    if (!request.containsHeader(HttpHeaders.AUTHORIZATION)) {
                        request.addHeader(HttpHeaders.AUTHORIZATION, authHeader);
                    }
                })
                // Interceptor 2: Fix Spring WS "Header already present" errors
                .addRequestInterceptorFirst((request, entity, context) -> {
                    if (request.containsHeader(HttpHeaders.CONTENT_LENGTH)) request.removeHeaders(HttpHeaders.CONTENT_LENGTH);
                    if (request.containsHeader(HttpHeaders.TRANSFER_ENCODING)) request.removeHeaders(HttpHeaders.TRANSFER_ENCODING);
                })
                .build();
    }

    /**
     * BEAN 3: Web Service Template (Application)
     * The actual object you inject into your Service classes.
     */
    @Bean
    public WebServiceTemplate case360WebServiceTemplate(
            @Qualifier("marshaller") Jaxb2Marshaller marshaller, 
            @Qualifier("case360HttpClient") CloseableHttpClient httpClient) {
        
        WebServiceTemplate template = new WebServiceTemplate(marshaller);
        template.setDefaultUri(case360Url);
        template.setMessageSender(new HttpComponents5MessageSender(httpClient));
        return template;
    }
}
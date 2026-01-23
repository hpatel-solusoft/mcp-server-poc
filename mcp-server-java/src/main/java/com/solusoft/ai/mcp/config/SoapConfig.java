package com.solusoft.ai.mcp.config;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.transport.http.HttpComponents5MessageSender;
import org.springframework.ws.transport.http.HttpUrlConnectionMessageSender;

@Configuration
public class SoapConfig {

    @Value("${case360.url}")
    private String case360Url;

    @Value("${case360.username}")
    private String username;

    @Value("${case360.password}")
    private String password;

    @Bean
    public Jaxb2Marshaller marshaller() {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        // MUST match the package where your WSDL classes were generated
        marshaller.setContextPath("com.solusoft.ai.mcp.integration.case360.soap");
        return marshaller;
    }
    
    @Bean
    public WebServiceTemplate case360WebServiceTemplate(Jaxb2Marshaller marshaller) {
        WebServiceTemplate template = new WebServiceTemplate(marshaller);
        template.setDefaultUri(case360Url);

        // 1. Connection Timeouts (Vital for Production)
        // Prevent the server from hanging indefinitely if Case360 is down
        RequestConfig requestConfig = RequestConfig.custom()
                //.setConnectTimeout(Timeout.of(5, TimeUnit.SECONDS))
                .setResponseTimeout(Timeout.of(30, TimeUnit.SECONDS))
                .build();

        // 2. Auth Provider
        BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(
                new AuthScope(null, -1),
                new UsernamePasswordCredentials(username, password.toCharArray())
        );

        // 3. Build the Robust Client
        CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultCredentialsProvider(credsProvider)
                .setDefaultRequestConfig(requestConfig)

                // INTERCEPTOR 1: Pre-emptive Authentication
                // Forces the Auth header to be sent immediately
                .addRequestInterceptorFirst((request, entity, context) -> {
                    String auth = username + ":" + password;
                    String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
                    request.addHeader(HttpHeaders.AUTHORIZATION, "Basic " + encodedAuth);
                })

                // INTERCEPTOR 2: The "Spring Restore" Fix
                // Because we used .custom(), we must manually remove these headers 
                // to prevent the "Content-Length already present" error.
                .addRequestInterceptorFirst((request, entity, context) -> {
                    if (request.containsHeader(HttpHeaders.CONTENT_LENGTH)) {
                        request.removeHeaders(HttpHeaders.CONTENT_LENGTH);
                    }
                    if (request.containsHeader(HttpHeaders.TRANSFER_ENCODING)) {
                        request.removeHeaders(HttpHeaders.TRANSFER_ENCODING);
                    }
                })
                .build();

        // 4. Register with Spring WS
        template.setMessageSender(new HttpComponents5MessageSender(httpClient));
        
        return template;
    }
    
}
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

    //@Bean
    public WebServiceTemplate case360WebServiceTemplate0(Jaxb2Marshaller marshaller) {
        WebServiceTemplate template = new WebServiceTemplate(marshaller);
        template.setDefaultUri(case360Url);

        // --- ROBUST AUTHENTICATION SETUP ---
        
        // 1. Create Credentials Provider
        BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(
                new AuthScope(null, -1), // Apply to any host/port
                new UsernamePasswordCredentials(username, password.toCharArray())
        );

        // 2. Build HttpClient that forces Pre-emptive Auth
        CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultCredentialsProvider(credsProvider)
                // This Interceptor forces the Auth header to be sent immediately
                // preventing the "401 Challenge" delay or rejection.
                .addRequestInterceptorFirst((request, entity, context) -> {
                     request.addHeader("Authorization", 
                        "Basic " + java.util.Base64.getEncoder().encodeToString(
                            (username + ":" + password).getBytes()
                        ));
                })
                .build();

        // 3. Attach to Template
        HttpComponents5MessageSender messageSender = new HttpComponents5MessageSender(httpClient);
        template.setMessageSender(messageSender);

        return template;
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
    
    
    //@Bean
    public WebServiceTemplate case360WebServiceTemplateJDK(Jaxb2Marshaller marshaller) {
        WebServiceTemplate template = new WebServiceTemplate(marshaller);
        template.setDefaultUri(case360Url);

        // Use the Simple JDK Message Sender
        HttpUrlConnectionMessageSender sender = new HttpUrlConnectionMessageSender() {
            @Override
            protected void prepareConnection(HttpURLConnection connection) throws IOException {
                super.prepareConnection(connection);
                // Manually add Basic Auth Header
                String auth = username + ":" + password;
                String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
                connection.setRequestProperty("Authorization", "Basic " + encodedAuth);
            }
        };

        template.setMessageSender(sender);
        return template;
    }
   
}
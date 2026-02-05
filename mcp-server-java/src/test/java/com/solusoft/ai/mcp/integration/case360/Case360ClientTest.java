package com.solusoft.ai.mcp.integration.case360;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ws.test.client.MockWebServiceServer;
import org.springframework.ws.test.client.RequestMatchers;
import org.springframework.ws.test.client.ResponseCreators;
import org.springframework.xml.transform.ResourceSource;

import com.solusoft.ai.mcp.AbstractBaseTest;

// Cleaner: Extends base class, no messy property annotations here
public class Case360ClientTest extends AbstractBaseTest {

    @Autowired
    private Case360Client client;

    @Autowired
    private ApplicationContext applicationContext;

    private MockWebServiceServer mockServer;

    @BeforeEach
    public void setup() {
        mockServer = MockWebServiceServer.createServer(applicationContext);
    }

    @Test
    public void testGetCaseFolderTemplateId_Success() throws Exception {
        ResourceSource request = new ResourceSource(new ClassPathResource("soap/requests/get-template-id-case.xml"));
        ResourceSource response = new ResourceSource(new ClassPathResource("soap/responses/get-template-id-case.xml"));

        mockServer.expect(RequestMatchers.payload(request))
                  .andRespond(ResponseCreators.withPayload(response));

        BigDecimal result = client.getCaseFolderTemplateId("Motor Claim");
        assertEquals(new BigDecimal("42"), result);
    }

    // ... (Your other tests remain exactly the same) ...
    
    @Test
    public void testCreateCase_Success() throws Exception {
        ResourceSource request = new ResourceSource(new ClassPathResource("soap/requests/create-case.xml"));
        ResourceSource response = new ResourceSource(new ClassPathResource("soap/responses/create-case.xml"));

        mockServer.expect(RequestMatchers.payload(request))
                  .andRespond(ResponseCreators.withPayload(response));

        String result = client.createCase(new BigDecimal("42"));
        assertTrue(result.contains("999"));
    }
}
package com.solusoft.ai.mcp.integration.case360;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
// Correct Imports for Client Testing
import org.springframework.ws.test.client.MockWebServiceServer;
import org.springframework.ws.test.client.RequestMatchers;
import org.springframework.ws.test.client.ResponseCreators;
import org.springframework.xml.transform.ResourceSource;

import com.solusoft.ai.mcp.McpServerApplication;

@SpringBootTest(classes = McpServerApplication.class)
public class Case360ClientTest {

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

    @Test
    public void testGetFilestoreTemplateId_Success() throws Exception {
        ResourceSource request = new ResourceSource(new ClassPathResource("soap/requests/get-template-id-filestore.xml"));
        ResourceSource response = new ResourceSource(new ClassPathResource("soap/responses/get-template-id-filestore.xml"));

        mockServer.expect(RequestMatchers.payload(request))
                  .andRespond(ResponseCreators.withPayload(response));

        BigDecimal result = client.getFilestoreTemplateId("Claim Document");
        assertEquals(new BigDecimal("123"), result);
    }

    @Test
    public void testCreateCase_Success() throws Exception {
        ResourceSource request = new ResourceSource(new ClassPathResource("soap/requests/create-case.xml"));
        ResourceSource response = new ResourceSource(new ClassPathResource("soap/responses/create-case.xml"));

        mockServer.expect(RequestMatchers.payload(request))
                  .andRespond(ResponseCreators.withPayload(response));

        String result = client.createCase(new BigDecimal("42"));
        assertTrue(result.contains("999"));
    }

    @Test
    public void testCreateFileStore_Success() throws Exception {
        ResourceSource request = new ResourceSource(new ClassPathResource("soap/requests/create-filestore.xml"));
        ResourceSource response = new ResourceSource(new ClassPathResource("soap/responses/create-filestore.xml"));

        mockServer.expect(RequestMatchers.payload(request))
                  .andRespond(ResponseCreators.withPayload(response));

        String result = client.createFileStore(new BigDecimal("55"));
        assertTrue(result.contains("555"));
    }

    @Test
    public void testUpdateCaseFields_Flow() throws Exception {
        ResourceSource getRequest = new ResourceSource(new ClassPathResource("soap/requests/get-fields.xml"));
        ResourceSource getResponse = new ResourceSource(new ClassPathResource("soap/responses/get-fields.xml"));

        ResourceSource setRequest = new ResourceSource(new ClassPathResource("soap/requests/set-fields.xml"));
        ResourceSource setResponse = new ResourceSource(new ClassPathResource("soap/responses/set-fields.xml"));

        mockServer.expect(RequestMatchers.payload(getRequest))
                  .andRespond(ResponseCreators.withPayload(getResponse));
        
        mockServer.expect(RequestMatchers.payload(setRequest))
                  .andRespond(ResponseCreators.withPayload(setResponse));

        client.updateCaseFields("123", Map.of("CLAIMANT_NAME", "New Name"));
    }

    @Test
    public void testUploadDocument_Success() throws Exception {
        ResourceSource request = new ResourceSource(new ClassPathResource("soap/requests/upload-document.xml"));
        ResourceSource response = new ResourceSource(new ClassPathResource("soap/responses/upload-document.xml"));

        mockServer.expect(RequestMatchers.payload(request))
                  .andRespond(ResponseCreators.withPayload(response));

        client.uploadDocument(new BigDecimal("777"), new byte[] {1,2,3}, "file.bin");
    }
}
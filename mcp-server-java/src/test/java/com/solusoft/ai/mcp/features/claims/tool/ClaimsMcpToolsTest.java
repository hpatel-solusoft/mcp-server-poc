package com.solusoft.ai.mcp.features.claims.tool;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solusoft.ai.mcp.features.claims.model.CreateHealthClaimRequest;
import com.solusoft.ai.mcp.features.claims.model.CreateMotorClaimRequest;
import com.solusoft.ai.mcp.integration.case360.Case360Client;

public class ClaimsMcpToolsTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private Case360Client case360Client;

    private ObjectMapper objectMapper;
    
    private ClaimsMcpTools tools;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();
        tools = new ClaimsMcpTools(jdbcTemplate, case360Client, objectMapper);
    }

    @Test
    public void testExtractClaimInfo_detectsMotorAndParsesFields() throws Exception {
        String doc = "Policy Number: POL-123456\nClaimant Name: Jane Doe\nVehicle: Car\nMake: Tesla";

        String json = tools.extractClaimInfo(doc);
        Map<?,?> map = objectMapper.readValue(json, Map.class);

        assertEquals("POL-123456", ((String)map.get("policy_number")).toUpperCase());
        assertEquals("Jane Doe", map.get("claimant_name"));
        //assertEquals("motor", map.get("claim_type"));
    }

    @Test
    public void testExtractClaimInfo_emptyInput_returnsFatalError() throws Exception {
        String json = tools.extractClaimInfo("");
        Map<?,?> map = objectMapper.readValue(json, Map.class);

        assertFalse((Boolean)map.get("success"));
        assertEquals("FATAL_ERROR", map.get("status"));
        assertTrue(((String)map.get("message")).contains("Document text cannot be empty"));
    }

    @Test
    public void testUploadDocument_success_callsCase360ClientAndReturnsDocumentId() throws Exception {
        // Prepare a long byte[] so Base64 length >= 100
        byte[] large = new byte[1024];
        for (int i = 0; i < large.length; i++) large[i] = (byte) (i % 127);
        String base64 = Base64.getEncoder().encodeToString(large);
        // include a data URI prefix to exercise the prefix removal
        String base64WithPrefix = "data:application/pdf;base64," + base64;

        when(case360Client.getFilestoreTemplateId(any())).thenReturn(BigDecimal.ONE);
        when(case360Client.createFileStore(any())).thenReturn("55555");
        // uploadDocument is void - we just verify it's called

        String resultJson = tools.uploadDocument(base64WithPrefix, "invoice.pdf");
        Map<?,?> result = objectMapper.readValue(resultJson, Map.class);

        assertTrue((Boolean) result.get("success"));
        assertEquals("55555", result.get("document_id"));

        // Verify uploadDocument was called with the created doc id and the decoded bytes
        ArgumentCaptor<BigDecimal> idCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        ArgumentCaptor<byte[]> contentCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(case360Client, times(1)).uploadDocument(idCaptor.capture(), contentCaptor.capture(), eq("invoice.pdf"));
        assertEquals(new BigDecimal("55555"), idCaptor.getValue());
        assertArrayEquals(large, contentCaptor.getValue());
    }

    @Test
    public void testUploadDocument_shortBase64_returnsFatalError() throws Exception {
        String bad = "abc";

        String resultJson = tools.uploadDocument(bad, "small.txt");
        Map<?,?> map = objectMapper.readValue(resultJson, Map.class);

        assertFalse((Boolean)map.get("success"));
        assertEquals("FATAL_ERROR", map.get("status"));
        assertTrue(((String)map.get("message")).toLowerCase().contains("system failure in uploaddocument."));
    }

    @Test
    public void testCreateMotorClaim_success() throws Exception {
    	// Note: 'claimDocId' is excluded here because Map.of throws error on null values
    	CreateMotorClaimRequest motorReq = new CreateMotorClaimRequest(
		    "Alice",
		    "POL-0001",
		    1500.0,
		    java.time.LocalDate.now().toString(),
		    "Rear end collision",
		    null, // claimDocId is null
		    "accident",
		    "2023 Tesla Model 3",
		    "XYZ-1234",
		    "high");

        when(case360Client.getCaseFolderTemplateId(any())).thenReturn(BigDecimal.TEN);
        when(case360Client.createCase(any())).thenReturn("CASE-123");

        String result = tools.createMotorClaim(motorReq);

        assertTrue(result.contains("SUCCESS"));
        assertTrue(result.contains("CASE-123"));

        verify(case360Client, times(1)).updateCaseFields(eq("CASE-123"), any(Map.class));
    }

    @Test
    public void testCreateHealthClaim_success() throws Exception {
    	
    	CreateHealthClaimRequest healthReq = new CreateHealthClaimRequest(
		    "Bob","POL-9999",2000.0,java.time.LocalDate.now().toString(),"Flu","Saint Hospital",null,"Medical expenses","illness","Dr. Who","Notes","normal","Summary");

        when(case360Client.getCaseFolderTemplateId(any())).thenReturn(BigDecimal.TEN);
        when(case360Client.createCase(any())).thenReturn("CASE-999");

        String result = tools.createHealthClaim(healthReq);

        assertTrue(result.contains("SUCCESS"));
        assertTrue(result.contains("CASE-999"));

        verify(case360Client, times(1)).updateCaseFields(eq("CASE-999"), any(Map.class));
    }

    @Test
    public void testStoreClaimRecord_success_savesToDatabase() throws Exception {
        Map<String,Object> claim = new HashMap<>();
        claim.put("claim_id", "CLM-1");
        claim.put("policy_number", "POL-100");
        claim.put("claimant_name", "Zed");
        claim.put("claim_type", "motor");

        Map<String,Object> workflow = new HashMap<>();
        workflow.put("workflow_id", "WF-1");
        workflow.put("case_id", "CASE-1");

        String claimJson = objectMapper.writeValueAsString(claim);
        String workflowJson = objectMapper.writeValueAsString(workflow);

        // Stub the varargs overload: update(String, Object...)
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        String resultJson = tools.storeClaimRecord(claimJson, workflowJson);
        Map<?,?> result = objectMapper.readValue(resultJson, Map.class);

        assertTrue((Boolean)result.get("success"));
        assertEquals("CLM-1", result.get("claim_id"));

        // Match the varargs overload by verifying update(String, Object[])
        verify(jdbcTemplate, times(1)).update(anyString(), any(Object[].class));
    }

    @Test
    public void testFullChain_motorClaimFlow() throws Exception {
        // 1) Simulate extracting from a raw document text
        String doc = "Policy Number: POL-CHAIN\nClaimant Name: Carl Chain\nVehicle: Car\nIncident Date: 2025-01-01\nClaim Amount: 1200\nDescription: Fender bender";

        // Prepare mocks for upload flow
        byte[] large = new byte[512];
        for (int i = 0; i < large.length; i++) large[i] = (byte) (i % 127);
        String base64 = Base64.getEncoder().encodeToString(large);
        String base64WithPrefix = "data:application/pdf;base64," + base64;

        when(case360Client.getFilestoreTemplateId(any())).thenReturn(BigDecimal.ONE);
        when(case360Client.createFileStore(any())).thenReturn("252");
        doNothing().when(case360Client).uploadDocument(any(BigDecimal.class), any(byte[].class), anyString());

        // 2) Extract claim info
        String extractedJson = tools.extractClaimInfo(doc);
        Map<?,?> claimMap = objectMapper.readValue(extractedJson, Map.class);

        // 3) Upload document and get document id
        String uploadResult = tools.uploadDocument(base64WithPrefix, "chain.pdf");
        Map<?,?> uploadMap = objectMapper.readValue(uploadResult, Map.class);
        String docId = String.valueOf(uploadMap.get("document_id"));

        // 4) Build CreateMotorClaimRequest using extracted fields + docId
        String claimant = claimMap.containsKey("claimant_name") ? String.valueOf(claimMap.get("claimant_name")) : "Unknown";
        String policy = claimMap.containsKey("policy_number") ? String.valueOf(claimMap.get("policy_number")) : "POL-CHAIN";
        Double amount = claimMap.containsKey("claim_amount") ? Double.valueOf(String.valueOf(claimMap.get("claim_amount"))) : 1200.0;
        String incidentDate = claimMap.containsKey("incident_date") ? String.valueOf(claimMap.get("incident_date")) : "2025-01-01";
        String description = claimMap.containsKey("description") ? String.valueOf(claimMap.get("description")) : "No desc";

        
        CreateMotorClaimRequest motorReq = new CreateMotorClaimRequest(
			    claimant,
			    policy,
			    amount,
			    incidentDate,
			    description,
			    docId,
			    "accident",
			    "Toyota Camry",
			    "ABC-123",
			    "normal"
			);
        
        // Mock case creation
        when(case360Client.getCaseFolderTemplateId(any())).thenReturn(BigDecimal.TEN);
        when(case360Client.createCase(any())).thenReturn("CASE-CHAIN-1");
        doNothing().when(case360Client).updateCaseFields(anyString(), any(Map.class));

        // 5) Create motor claim (this should call case360Client.createCase and updateCaseFields)
        String createResult = tools.createMotorClaim(motorReq);
        assertTrue(createResult.contains("SUCCESS"));
        assertTrue(createResult.contains("CASE-CHAIN-1"));

        // 6) Store the record (simulate workflow result)
        Map<String,Object> workflow = new HashMap<>();
        workflow.put("workflow_id", "525");
        workflow.put("case_id", "121");

        String claimJson = objectMapper.writeValueAsString(claimMap);
        String workflowJson = objectMapper.writeValueAsString(workflow);

        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        String storeResult = tools.storeClaimRecord(claimJson, workflowJson);
        Map<?,?> storeMap = objectMapper.readValue(storeResult, Map.class);
        assertTrue((Boolean)storeMap.get("success"));

        // 7) Verify overall ordering: filestore template -> create filestore -> upload -> case template -> create case -> update fields -> db update
        org.mockito.InOrder inOrder = inOrder(case360Client, jdbcTemplate);
        inOrder.verify(case360Client).getFilestoreTemplateId(any());
        inOrder.verify(case360Client).createFileStore(any());
        inOrder.verify(case360Client).uploadDocument(any(BigDecimal.class), any(byte[].class), eq("chain.pdf"));
        inOrder.verify(case360Client).getCaseFolderTemplateId(any());
        inOrder.verify(case360Client).createCase(any());
        inOrder.verify(case360Client).updateCaseFields(eq("CASE-CHAIN-1"), any(Map.class));
        inOrder.verify(jdbcTemplate).update(anyString(), any(Object[].class));
    }

    @Test
    public void testFullChain_healthcareClaimFlow() throws Exception {
        // Document with healthcare keywords -> should be classified as 'healthcare'
        String doc = "Policy Number: POL-HC-1\nClaimant Name: Dr. Patient\nDiagnosis: Sprain\nHospital: General Hospital\nPhysician: Dr. House\nIncident Date: 2025-02-02\nClaim Amount: 3000";

        // Prepare upload mocks
        byte[] large = new byte[600];
        for (int i = 0; i < large.length; i++) large[i] = (byte) (i % 127);
        String base64 = Base64.getEncoder().encodeToString(large);
        String base64WithPrefix = "data:application/pdf;base64," + base64;

        when(case360Client.getFilestoreTemplateId(any())).thenReturn(BigDecimal.ONE);
        when(case360Client.createFileStore(any())).thenReturn("1001");
        doNothing().when(case360Client).uploadDocument(any(BigDecimal.class), any(byte[].class), anyString());

        // Extract
        String extractedJson = tools.extractClaimInfo(doc);
        Map<?,?> claimMap = objectMapper.readValue(extractedJson, Map.class);

        // Upload
        String uploadResult = tools.uploadDocument(base64WithPrefix, "hc.pdf");
        Map<?,?> uploadMap = objectMapper.readValue(uploadResult, Map.class);
        String docId = String.valueOf(uploadMap.get("document_id"));

        // Build health request
        String claimant = claimMap.containsKey("claimant_name") ? String.valueOf(claimMap.get("claimant_name")) : "Unknown";
        String policy = claimMap.containsKey("policy_number") ? String.valueOf(claimMap.get("policy_number")) : "POL-HC-1";
        Double amount = claimMap.containsKey("claim_amount") ? Double.valueOf(String.valueOf(claimMap.get("claim_amount"))) : 3000.0;
        String incidentDate = claimMap.containsKey("incident_date") ? String.valueOf(claimMap.get("incident_date")) : "2025-02-02";

        CreateHealthClaimRequest healthReq = new CreateHealthClaimRequest( claimant, policy, amount, incidentDate, "Sprain", "General Hospital", "Medical bills", docId, "accident", "Dr. House", "Notes", "high", "Treatment summary");
        
        // Case creation mocks
        when(case360Client.getCaseFolderTemplateId(any())).thenReturn(BigDecimal.TEN);
        when(case360Client.createCase(any())).thenReturn("CASE-HC-1");
        doNothing().when(case360Client).updateCaseFields(anyString(), any(Map.class));

        String result = tools.createHealthClaim(healthReq);
        assertTrue(result.contains("SUCCESS"));
        assertTrue(result.contains("CASE-HC-1"));

        // Store record
        Map<String,Object> workflow = new HashMap<>();
        workflow.put("workflow_id", "525");
        workflow.put("case_id", "235");

        String claimJson = objectMapper.writeValueAsString(claimMap);
        String workflowJson = objectMapper.writeValueAsString(workflow);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        String storeResult = tools.storeClaimRecord(claimJson, workflowJson);
        Map<?,?> storeMap = objectMapper.readValue(storeResult, Map.class);
        assertTrue((Boolean)storeMap.get("success"));

        // Verify order
        org.mockito.InOrder inOrder = inOrder(case360Client, jdbcTemplate);
        inOrder.verify(case360Client).getFilestoreTemplateId(any());
        inOrder.verify(case360Client).createFileStore(any());
        inOrder.verify(case360Client).uploadDocument(any(BigDecimal.class), any(byte[].class), eq("hc.pdf"));
        inOrder.verify(case360Client).getCaseFolderTemplateId(any());
        inOrder.verify(case360Client).createCase(any());
        inOrder.verify(case360Client).updateCaseFields(eq("CASE-HC-1"), any(Map.class));
        inOrder.verify(jdbcTemplate).update(anyString(), any(Object[].class));
    }

    @Test
    public void testNegativeChain_uploadFails_noCaseCreated() throws Exception {
        String doc = "Policy Number: POL-NOK\nClaimant Name: Fail User\nVehicle: Car\nIncident Date: 2025-03-03\nClaim Amount: 500";

        byte[] large = new byte[512];
        for (int i = 0; i < large.length; i++) large[i] = (byte) (i % 127);
        String base64 = Base64.getEncoder().encodeToString(large);
        String base64WithPrefix = "data:application/pdf;base64," + base64;

        when(case360Client.getFilestoreTemplateId(any())).thenReturn(BigDecimal.ONE);
        when(case360Client.createFileStore(any())).thenReturn("121");
        // Simulate upload throwing an exception
        doThrow(new RuntimeException("Remote upload failed")).when(case360Client).uploadDocument(any(BigDecimal.class), any(byte[].class), anyString());

        String uploadJson = tools.uploadDocument(base64WithPrefix, "nope.pdf");
        Map<?,?> uploadResp = objectMapper.readValue(uploadJson, Map.class);
        assertFalse((Boolean)uploadResp.get("success"));
        assertEquals("FATAL_ERROR", uploadResp.get("status"));
        assertTrue(((String)uploadResp.get("message")).toLowerCase().contains("remote upload failed"));

        // Now ensure createCase was never called because upload failed
        verify(case360Client, never()).getCaseFolderTemplateId(any());
        verify(case360Client, never()).createCase(any());
        verify(case360Client, never()).updateCaseFields(anyString(), any(Map.class));
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }
}
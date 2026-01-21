package com.solusoft.ai.mcp.features.claims.tool;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule; // <--- 1. ADD IMPORT
import com.solusoft.ai.mcp.features.claims.model.Claim;
import com.solusoft.ai.mcp.features.claims.model.CreateHealthClaimRequest;
import com.solusoft.ai.mcp.features.claims.model.CreateMotorClaimRequest;
import com.solusoft.ai.mcp.features.claims.model.StoreClaimRequest;
import com.solusoft.ai.mcp.features.claims.repository.ClaimRepository;
import com.solusoft.ai.mcp.integration.case360.Case360Client;

public class ClaimsMcpToolsTest {

    @Mock
    private ClaimRepository claimRepository;
    
    @Mock
    private Case360Client case360Client;

    private ObjectMapper objectMapper;
    
    private ClaimsMcpTools tools;
    
    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule()); // <--- 2. REGISTER MODULE
        
        tools = new ClaimsMcpTools(claimRepository, case360Client, objectMapper);
    }

    @Test
    public void testExtractClaimInfo_detectsMotorAndParsesFields() throws Exception {
        String doc = "Policy Number: POL-123456\nClaimant Name: Jane Doe\nVehicle: Car\nMake: Tesla";

        String json = tools.extractClaimInfo(doc);
        Map<?,?> map = objectMapper.readValue(json, Map.class);

        assertEquals("POL-123456", ((String)map.get("policy_number")).toUpperCase());
        assertEquals("Jane Doe", map.get("claimant_name"));
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
        byte[] pdfHeader = "%PDF-1.5".getBytes();
        byte[] large = new byte[1024];
        System.arraycopy(pdfHeader, 0, large, 0, pdfHeader.length);
        for (int i = pdfHeader.length; i < large.length; i++) {
            large[i] = (byte) (i % 127);
        }
        // --- END FIX ---
        String base64 = Base64.getEncoder().encodeToString(large);
        String base64WithPrefix = "data:application/pdf;base64," + base64;

        when(case360Client.getFilestoreTemplateId(any())).thenReturn(BigDecimal.ONE);
        when(case360Client.createFileStore(any())).thenReturn("55555");

        String resultJson = tools.uploadDocument(base64WithPrefix, "invoice.pdf");
        Map<?,?> result = objectMapper.readValue(resultJson, Map.class);

        assertTrue((Boolean) result.get("success"));
        assertEquals("55555", result.get("document_id"));

        ArgumentCaptor<BigDecimal> idCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        ArgumentCaptor<byte[]> contentCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<String> filenameCaptor = ArgumentCaptor.forClass(String.class);
        verify(case360Client, times(1)).uploadDocument(idCaptor.capture(), contentCaptor.capture(), filenameCaptor.capture());
        assertEquals(new BigDecimal("55555"), idCaptor.getValue());
        assertArrayEquals(large, contentCaptor.getValue());
        // The implementation now generates a UUID-based filename; ensure it preserves the original extension
        assertTrue(filenameCaptor.getValue().toLowerCase().endsWith(".pdf"));
    }

    @Test
    public void testUploadDocument_shortBase64_returnsFatalError() throws Exception {
        String bad = "abc";

        String resultJson = tools.uploadDocument(bad, "small.txt");
        Map<?,?> map = objectMapper.readValue(resultJson, Map.class);

        assertFalse((Boolean)map.get("success"));
        assertEquals("FATAL_ERROR", map.get("status"));
        // New implementation returns tool name 'upload_document' in the message
        assertTrue(((String)map.get("message")).toLowerCase().contains("upload_document"));
    }

    @Test
    public void testCreateMotorClaim_success() throws Exception {
    	CreateMotorClaimRequest motorReq = new CreateMotorClaimRequest(
    	    "Alice",
    	    "POL-0001",
    	    new BigDecimal(1500.0),
    	    java.time.LocalDate.now(),
    	    "Rear end collision",
    	    null, 
    	    "accident",
    	    "2023 Tesla Model 3",
    	    "XYZ-1234",
    	    "high");

        when(case360Client.getCaseFolderTemplateId(any())).thenReturn(BigDecimal.TEN);
        when(case360Client.createCase(any())).thenReturn("123");

        String result = tools.createMotorClaim(motorReq);

        Map<?,?> resp = objectMapper.readValue(result, Map.class);
        assertEquals("success", resp.get("status"));
        assertEquals("123", resp.get("case_id"));
        assertEquals(motorReq.claimDocId(), resp.get("claim_doc_id"));

        verify(case360Client, times(1)).updateCaseFields(eq("123"), any(Map.class));
    }

    @Test
    public void testCreateHealthClaim_success() throws Exception {
    	CreateHealthClaimRequest healthReq = new CreateHealthClaimRequest(
    	    "Bob","POL-9999",new BigDecimal(2000.0),java.time.LocalDate.now(),"Flu","Saint Hospital",null,"Medical expenses","illness","Dr. Who","Notes","normal","Summary");

        when(case360Client.getCaseFolderTemplateId(any())).thenReturn(BigDecimal.TEN);
        when(case360Client.createCase(any())).thenReturn("999");

        String result = tools.createHealthClaim(healthReq);

        Map<?,?> resp = objectMapper.readValue(result, Map.class);
        assertEquals("success", resp.get("status"));
        assertEquals("999", resp.get("case_id"));
        assertEquals(healthReq.claimDocId(), resp.get("claim_doc_id"));

        verify(case360Client, times(1)).updateCaseFields(eq("999"), any(Map.class));
    }

    @Test
    public void testStoreClaimRecord_success_savesToDatabase() throws Exception {
        // Create the Request Object directly (not JSON strings)
        StoreClaimRequest request = new StoreClaimRequest(
            "12334324","28", "POL-100", "Zed", "motor", 
            new BigDecimal("500.00"), "CASE-1", 
            LocalDate.now(),"{vehicle_details: 2021 Elantra, color: blue}"
        );

        // Mock DB behavior: 
        // 1. findByClaimId returns empty (triggers insert)
        // 2. save returns the entity
        when(claimRepository.findByClaimId("12334324")).thenReturn(Optional.empty());
        when(claimRepository.save(any(Claim.class))).thenAnswer(i -> i.getArguments()[0]);

        String resultJson = tools.storeClaimRecord(request);
        Map<?,?> result = objectMapper.readValue(resultJson, Map.class);

        assertTrue((Boolean)result.get("success"));
        assertEquals("12334324", result.get("claim_id"));
        assertEquals("created", result.get("action"));

        // Verify save was called on the repository
        verify(claimRepository, times(1)).save(any(Claim.class));
    }

    @Test
    public void testFullChain_motorClaimFlow() throws Exception {
        // 1) Simulate extracting from a raw document text
        String doc = "Policy Number: POL-CHAIN\nClaimant Name: Carl Chain\nVehicle: Car\nIncident Date: 2025-01-01\nClaim Amount: 1200\nDescription: Fender bender";

        byte[] pdfHeader = "%PDF-1.5".getBytes(); 
        byte[] large = new byte[600];
        System.arraycopy(pdfHeader, 0, large, 0, pdfHeader.length);
        
        // Encode to Base64 as usual
        String base64WithPrefix = "data:application/pdf;base64," + Base64.getEncoder().encodeToString(large);

        when(case360Client.getFilestoreTemplateId(any())).thenReturn(BigDecimal.ONE);
        when(case360Client.createFileStore(any())).thenReturn("252");
        doNothing().when(case360Client).uploadDocument(any(BigDecimal.class), any(byte[].class), anyString());

        // 2) Extract claim info
        String extractedJson = tools.extractClaimInfo(doc);
        Map<?,?> claimMap = objectMapper.readValue(extractedJson, Map.class);

        // 3) Upload document
        String uploadResult = tools.uploadDocument(base64WithPrefix, "chain.pdf");
        Map<?,?> uploadMap = objectMapper.readValue(uploadResult, Map.class);
        String docId = String.valueOf(uploadMap.get("document_id"));

        // 4) Create Motor Claim
        CreateMotorClaimRequest motorReq = new CreateMotorClaimRequest(
		    (String)claimMap.get("claimant_name"),
		    (String)claimMap.get("policy_number"),
		    new BigDecimal("1200"),
		    LocalDate.parse((String)claimMap.get("incident_date")),
		    "Fender bender",
		    docId,
		    "accident",
		    "Toyota Camry",
		    "ABC-123",
		    "normal"
		);
        
        when(case360Client.getCaseFolderTemplateId(any())).thenReturn(BigDecimal.TEN);
        when(case360Client.createCase(any())).thenReturn("CASE-CHAIN-1");
        doNothing().when(case360Client).updateCaseFields(anyString(), any(Map.class));

        String createResult = tools.createMotorClaim(motorReq);
        Map<?,?> createResp = objectMapper.readValue(createResult, Map.class);
        assertEquals("success", createResp.get("status"));

        // 6) Store the record
        // Construct the Request object for storage
        StoreClaimRequest storeReq = new StoreClaimRequest(
            "23423432", 
            motorReq.claimDocId(),// ID generated in real flow, hardcoded for test input
            motorReq.policyNumber(),
            motorReq.claimantName(),
            "motor",
            motorReq.claimAmount(),
            "1234",
            motorReq.incidentDate(),
            "{vehicle_make: Toyota Camry, license_plate_number: ABC-123}"
        );

        when(claimRepository.findByClaimId(anyString())).thenReturn(Optional.empty());
        when(claimRepository.save(any(Claim.class))).thenAnswer(i -> i.getArguments()[0]);

        String storeResult = tools.storeClaimRecord(storeReq);
        Map<?,?> storeMap = objectMapper.readValue(storeResult, Map.class);
        assertTrue((Boolean)storeMap.get("success"));

        // 7) Verify overall ordering
        // Note: We replaced jdbcTemplate with claimRepository
        org.mockito.InOrder inOrder = inOrder(case360Client, claimRepository);
        inOrder.verify(case360Client).getFilestoreTemplateId(any());
        inOrder.verify(case360Client).createFileStore(any());
        inOrder.verify(case360Client).uploadDocument(
            any(BigDecimal.class), 
            any(byte[].class), 
            org.mockito.ArgumentMatchers.endsWith(".pdf") // checks extension only
        );
        inOrder.verify(case360Client).getCaseFolderTemplateId(any());
        inOrder.verify(case360Client).createCase(any());
        inOrder.verify(case360Client).updateCaseFields(eq("CASE-CHAIN-1"), any(Map.class));
        // Verify DB Save called last
        inOrder.verify(claimRepository).save(any(Claim.class));
    }

    @Test
    public void testFullChain_healthcareClaimFlow() throws Exception {
        String doc = "Policy Number: POL-HC-1\nClaimant Name: Dr. Patient\nDiagnosis: Sprain\nHospital: General Hospital\nPhysician: Dr. House\nIncident Date: 2025-02-02\nClaim Amount: 3000";

        byte[] pdfHeader = "%PDF-1.5".getBytes(); 
        byte[] large = new byte[600];
        System.arraycopy(pdfHeader, 0, large, 0, pdfHeader.length);
        
        // Encode to Base64 as usual
        String base64WithPrefix = "data:application/pdf;base64," + Base64.getEncoder().encodeToString(large);

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

        // Create Health Request
        CreateHealthClaimRequest healthReq = new CreateHealthClaimRequest(
                (String)claimMap.get("claimant_name"),
                (String)claimMap.get("policy_number"),
                new BigDecimal("3000"),
                LocalDate.parse((String)claimMap.get("incident_date")),
                "Sprain", "General Hospital", "Medical bills", docId, 
                "accident", "Dr. House", "Notes", "high", "Summary");
        
        when(case360Client.getCaseFolderTemplateId(any())).thenReturn(BigDecimal.TEN);
        when(case360Client.createCase(any())).thenReturn("CASE-HC-1");
        doNothing().when(case360Client).updateCaseFields(anyString(), any(Map.class));

        String result = tools.createHealthClaim(healthReq);
        Map<?,?> resp = objectMapper.readValue(result, Map.class);
        assertEquals("success", resp.get("status"));

        // Store record
        StoreClaimRequest storeReq = new StoreClaimRequest(
            "21234321",
            "89",
            healthReq.policyNumber(),
            healthReq.claimantName(),
            "healthcare",
            healthReq.claimAmount(),
            "3242",
            healthReq.incidentDate(),
            "{diagnosis: Sprain, hospital: General Hospital, physician: Dr. House}"
        );

        when(claimRepository.findByClaimId(anyString())).thenReturn(Optional.empty());
        when(claimRepository.save(any(Claim.class))).thenAnswer(i -> i.getArguments()[0]);

        String storeResult = tools.storeClaimRecord(storeReq);
        Map<?,?> storeMap = objectMapper.readValue(storeResult, Map.class);
        assertTrue((Boolean)storeMap.get("success"));

        // Verify order
        org.mockito.InOrder inOrder = inOrder(case360Client, claimRepository);
        inOrder.verify(case360Client).getFilestoreTemplateId(any());
        inOrder.verify(case360Client).createFileStore(any());
        inOrder.verify(case360Client).uploadDocument(
            any(BigDecimal.class), 
            any(byte[].class), 
            org.mockito.ArgumentMatchers.endsWith(".pdf") // checks extension only
        );
        inOrder.verify(case360Client).getCaseFolderTemplateId(any());
        inOrder.verify(case360Client).createCase(any());
        inOrder.verify(case360Client).updateCaseFields(eq("CASE-HC-1"), any(Map.class));
        inOrder.verify(claimRepository).save(any(Claim.class));
    }

    @Test
    public void testUploadDocument_SecurityBlock_InvalidMimeType_MotorContext() throws Exception {
        // 1. Prepare "Malicious" or Invalid Data
        // A simple text string is detected as "text/plain", which is NOT in ALLOWED_MIME_TYPES
        String invalidContent = "This is a plain text file, not a PDF.";
        String base64Invalid = "data:text/plain;base64," + Base64.getEncoder().encodeToString(invalidContent.getBytes());

        // 2. Mock behavior (Get Template ID succeeds, but we expect failure before the actual upload)
        when(case360Client.getFilestoreTemplateId(any())).thenReturn(BigDecimal.ONE);
        when(case360Client.createFileStore(any())).thenReturn("999-FAIL");

        // 3. Execute Tool
        String resultJson = tools.uploadDocument(base64Invalid, "suspicious.txt");
        Map<?,?> resultMap = objectMapper.readValue(resultJson, Map.class);

        // 4. Assert Security Block
        // The tool catches the SecurityException and returns success: false
        assertFalse((Boolean) resultMap.get("success"), "Upload should fail for invalid MIME type");
        assertEquals("FATAL_ERROR", resultMap.get("status"));
        
        String message = (String) resultMap.get("message");
        assertTrue(message.contains("Security Block"), "Error message should mention Security Block");
        assertTrue(message.contains("text/plain"), "Error message should mention the detected type");

        // 5. Verify Isolation
        // Crucial: Ensure the backend upload method was NEVER called
        verify(case360Client, never()).uploadDocument(any(BigDecimal.class), any(byte[].class), anyString());
        
        // Ensure we didn't proceed to create a case or save a claim record
        verify(case360Client, never()).createCase(any());
        verify(claimRepository, never()).save(any(Claim.class));
    }

    @Test
    public void testUploadDocument_SecurityBlock_InvalidMimeType_HealthContext() throws Exception {
        // 1. Prepare Invalid Data (Raw binary zeros -> application/octet-stream)
        byte[] maliciousBytes = new byte[100]; // Just zeros, no PDF header
        String base64Invalid = Base64.getEncoder().encodeToString(maliciousBytes);

        // 2. Mock behavior
        when(case360Client.getFilestoreTemplateId(any())).thenReturn(BigDecimal.ONE);
        when(case360Client.createFileStore(any())).thenReturn("888-FAIL");

        // 3. Execute Tool
        String resultJson = tools.uploadDocument(base64Invalid, "fake_invoice.pdf"); // Name says PDF, content is not
        Map<?,?> resultMap = objectMapper.readValue(resultJson, Map.class);

        // 4. Assert Security Block
        assertFalse((Boolean) resultMap.get("success"), "Upload should fail for invalid MIME type");
        
        String message = (String) resultMap.get("message");
        assertTrue(message.contains("Security Block"), "Error message should mention Security Block");
        // Tika usually detects raw zeros as 'application/octet-stream'
        assertTrue(message.contains("application/octet-stream") || message.contains("application/x-tika-msoffice"), 
                   "Error message should detect the actual mime type, not rely on the extension");

        // 5. Verify Isolation
        verify(case360Client, never()).uploadDocument(any(BigDecimal.class), any(byte[].class), anyString());
        verify(case360Client, never()).createCase(any());
        verify(claimRepository, never()).save(any(Claim.class));
    }
    
    @Test
    public void testNegativeChain_uploadFails_noCaseCreated() throws Exception {
        byte[] large = new byte[512];
        String base64WithPrefix = "data:application/pdf;base64," + Base64.getEncoder().encodeToString(large);

        when(case360Client.getFilestoreTemplateId(any())).thenReturn(BigDecimal.ONE);
        when(case360Client.createFileStore(any())).thenReturn("121");
        
        doThrow(new RuntimeException("Remote upload failed")).when(case360Client).uploadDocument(any(BigDecimal.class), any(byte[].class), anyString());

        String uploadJson = tools.uploadDocument(base64WithPrefix, "nope.pdf");
        Map<?,?> uploadResp = objectMapper.readValue(uploadJson, Map.class);
        assertFalse((Boolean)uploadResp.get("success"));
        assertEquals("FATAL_ERROR", uploadResp.get("status"));

        // Ensure createCase and Repo Save were never called
        verify(case360Client, never()).getCaseFolderTemplateId(any());
        verify(case360Client, never()).createCase(any());
        verify(case360Client, never()).updateCaseFields(anyString(), any(Map.class));
        verify(claimRepository, never()).save(any(Claim.class));
    }
}
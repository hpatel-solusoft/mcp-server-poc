package com.solusoft.ai.mcp.features.claims.tool;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solusoft.ai.mcp.features.claims.model.Claim;
import com.solusoft.ai.mcp.features.claims.model.CreateHealthClaimRequest;
import com.solusoft.ai.mcp.features.claims.model.CreateMotorClaimRequest;
import com.solusoft.ai.mcp.features.claims.model.StoreClaimRequest;
import com.solusoft.ai.mcp.features.claims.repository.ClaimRepository;
import com.solusoft.ai.mcp.integration.case360.Case360Client;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ClaimsMcpTools {

    private final Case360Client case360Client;
    private final ObjectMapper objectMapper;
    private final ClaimRepository claimRepository;
    
    public ClaimsMcpTools(ClaimRepository claimRepository, Case360Client case360Client, ObjectMapper objectMapper) {
        this.case360Client = case360Client;
        this.objectMapper = objectMapper;
        this.claimRepository = claimRepository;
    }

    /*@PostConstruct
    public void initDatabase() {
        log.info("Entering initDatabase");
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS claims (
                    claim_id TEXT PRIMARY KEY,
                    policy_number TEXT,
                    claimant_name TEXT,
                    claim_type TEXT,
                    claim_amount REAL,
                    diagnosis TEXT,
                    incident_date TEXT,
                    hospital TEXT,
                    physician TEXT,
                    vehicle_make TEXT,
                    vehicle_model TEXT,
                    vehicle_year INTEGER,
                    incident_type TEXT,
                    claims_id TEXT,
                    case_id TEXT,
                    status TEXT,
                    created_at TEXT,
                    processed_at TEXT
                )
            """);
            log.info("✓ Database initialized");
        } catch (Exception e) {
            log.error("Failed to initialize database", e);
        }
        log.info("Exiting initDatabase");
    }*/
    
    @McpTool(name = "extract_claim_info", description = "Extracts claim fields from raw document text. Returns JSON.")
    public String extractClaimInfo(String documentText) {
        log.info("[TOOL] Entering extract_claim_info");
        log.debug("Input documentText: {}", documentText);
        
        try {
            if (documentText == null || documentText.isEmpty()) {
                throw new IllegalArgumentException("Document text cannot be empty");
            }

            Map<String, String> claimData = new HashMap<>();

            for (String line : documentText.split("\n")) {
                if (line.contains(":")) {
                    String[] parts = line.split(":", 2);
                    claimData.put(parts[0].trim().toLowerCase().replace(" ", "_"), parts[1].trim());
                }
            }
            
            String lowerText = documentText.toLowerCase();
            
            
            String claimType = (lowerText.contains("vehicle") || lowerText.contains("car")) ? "motor" : "healthcare";
            claimData.put("claim_type", claimType);
            
            
            String result = toJson(claimData);
            log.debug("Return value (JSON): {}", result);
            return result;
            
        } catch (Exception e) {
            return handleError("extractClaimInfo", e);
        }
    }
    
    @McpTool(description = "Uploads a base64 encoded document to Case360")
    public String uploadDocument(String documentBase64, String documentName) {
        log.info("[TOOL] Entering upload_document");
        log.debug("Input documentName: {}, base64Length: {}", documentName, (documentBase64 != null ? documentBase64.length() : 0));
        
        try {
            if (documentBase64 == null || documentBase64.isEmpty()) {
                throw new IllegalArgumentException("Base64 string is empty.");
            }

            if (documentBase64.contains(",")) {
                documentBase64 = documentBase64.substring(documentBase64.indexOf(",") + 1);
            }

            documentBase64 = documentBase64.replaceAll("\\s+", "");
            
            byte[] docBytes = Base64.getDecoder().decode(documentBase64);
            log.info("✓ Decoded {} KB of data.", docBytes.length / 1024);

            BigDecimal templateId = case360Client.getFilestoreTemplateId("Claim Document");
            String documentId = case360Client.createFileStore(templateId);
            
            case360Client.uploadDocument(new BigDecimal(documentId), docBytes, documentName);
            
            log.info("✓ Document uploaded successfully to Case360 with ID: {}", documentId);
            return toJson(Map.of("success", true, "document_id", documentId));
            
        } catch (Exception e) {
            log.error("❌ Base64 Decoding/Upload Failed.", e);
            return handleError("uploadDocument", e);
        }
    }
    

    @McpTool(
        name = "create_motor_claim", 
        description = "Creates a Motor Insurance Claim. Requires vehicle and accident details."
    )
    public String createMotorClaim(CreateMotorClaimRequest request) { // <--- 1. Strict Contract
        log.info("[TOOL] Entering create_motor_claim");
        log.debug("Claim Data: {}", request);
        try {

        	String claimId = String.valueOf(System.currentTimeMillis());
            @SuppressWarnings("unchecked")
            Map<String, Object> dynamicData = objectMapper.convertValue(request, Map.class);
            log.info("Converted Request to Map: {}", dynamicData);

            // 3. BACKEND: Use existing generic logic
            BigDecimal templateId = case360Client.getCaseFolderTemplateId("Motor Claim");
            String caseId = case360Client.createCase(templateId);
            
            Map<String, Object> updates = normalizeDataForBackend(dynamicData);
            
            updates.put("CREATED_ON", Instant.now());
            updates.put("CLAIM_ID", claimId);
            
            case360Client.updateCaseFields(caseId, updates);

            String result = String.format("SUCCESS: Motor Claim created. Case ID: %s. Fields processed: %d.", caseId, updates.size());
            log.info("[TOOL] Exiting create_motor_claim");
            return result;

        } catch (Exception e) {
            log.error("❌ create_motor_claim Failed.", e);
            return handleError("createMotorClaim", e);
        }
    }
    
    @McpTool(
        name = "create_healthcare_claim", 
        description = "Creates a Healthcare/Medical Claim. Requires diagnosis and hospital details."
    )
    public String createHealthClaim(CreateHealthClaimRequest request) { // <--- 1. Strict Contract
        log.info("[TOOL] Entering create_healthcare_claim");
        log.debug("Claim Data: {}", request);
        try {
        	String claimId = String.valueOf(System.currentTimeMillis());
        	
            @SuppressWarnings("unchecked")
            Map<String, Object> dynamicData = objectMapper.convertValue(request, Map.class);
            log.info("Converted Request to Map: {}", dynamicData);

            // 3. BACKEND: Use existing generic logic
            BigDecimal templateId = case360Client.getCaseFolderTemplateId("Healthcare Claim");
            String caseId = case360Client.createCase(templateId);
            
            Map<String, Object> updates = normalizeDataForBackend(dynamicData);
            
            updates.put("CREATED_ON", Instant.now());
            updates.put("CLAIM_ID", claimId);
            
            case360Client.updateCaseFields(caseId, updates);

            String result = String.format("SUCCESS: Healthcare Claim created. Case ID: %s. Fields processed: %d.", caseId, updates.size());
            log.info("[TOOL] Exiting create_healthcare_claim");
            return result;

        } catch (Exception e) {
            log.error("❌ create_healthcare_claim Failed.", e);
            return handleError("createHealthClaim", e);
        }
    }
    
    @McpTool(name="store_claim_record",
    		description = "stores claim record in the database")
    public String storeClaimRecord(StoreClaimRequest request) {
    	log.info("[TOOL] Entering store_claim_record");
        log.debug("Claim Data: {}", request);
        try {
	        // 1. Prepare the JSON Blob for dynamic data
	        Map<String, Object> dynamicData = new HashMap<>();
	        if ("motor".equalsIgnoreCase(request.claimType())) {
	            dynamicData.put("vehicle_make", request.vehicleMake());
	            dynamicData.put("incident_type", request.incidentType());
	            // ... add other auto fields ...
	        } else {
	            dynamicData.put("diagnosis", request.diagnosis());
	            // ... add other health fields ...
	        }
	        String jsonBlob = toJson(dynamicData); // Helper method to convert Map -> JSON String
	
	        Optional<Claim> existing = claimRepository.findByClaimId(request.claimId());
	
	        // 3. Determine the DB Primary Key (ID)
	        // If exists: Use the EXISTING ID (this triggers an UPDATE)
	        // If new: Use NULL (this triggers an INSERT with Auto-Generate)
	        Integer dbId = existing.map(Claim::id).orElse(null);
	
	        // 4. Create the Entity (Record)
	        Claim claimEntity = new Claim(
	            dbId, // <--- The magic happens here (null = Auto-Gen, 123 = Update)
	            request.claimId(),
	            request.policyNumber(),
	            request.claimantName(),
	            request.claimType(),
	            request.claimAmount(),
	            request.caseId(),
	            "submitted",
	            Instant.now(),
	            Instant.now(),
	            jsonBlob
	        );
	
	        // 5. Save (Spring handles the SQL for you)
	        claimRepository.save(claimEntity);
	        log.info("[TOOL] Exiting store_claim_record");
	        return toJson(Map.of(
	            "success", true, 
	            "claim_id", request.claimId(), 
	            "action", (dbId == null ? "created" : "updated")
	        ));
        } catch (Exception e) {
            log.error("❌ store_claim_record Failed.", e);
            e.printStackTrace();
            return handleError("storeClaimRecord", e);
        }
    }
    
    // -------------------------------------------------------------------------
    //  HELPER METHODS 
    // -------------------------------------------------------------------------

    private Map<String, Object> normalizeDataForBackend(Map<String, Object> input) {
        log.info("Normalizing data for backend. Input keys: {}", input != null ? input.keySet() : "null");
        if (input == null) {
            log.warn("normalizeDataForBackend received null input. Returning empty map.");
            return new HashMap<>();
        }
        
        Map<String, Object> output = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            String rawKey = entry.getKey();
            Object value = entry.getValue();

            String cleanKey = toSnakeCaseUpper(rawKey);
            
            if (cleanKey.contains("DATE") && value instanceof String) {
                try {
                    output.put(cleanKey, LocalDate.parse((String) value));
                } catch (Exception e) {
                    output.put(cleanKey, value); 
                }
            } else {
                output.put(cleanKey, value);
            }
        }
        return output;
    }

    private String toSnakeCaseUpper(String str) {
        String regex = "([a-z])([A-Z]+)";
        String replacement = "$1_$2";
        
        return str.replaceAll(regex, replacement)
                  .replace(" ", "_")
                  .replace("-", "_")
                  .toUpperCase();
    }

    private String toJson(Object data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            log.error("JSON Serialization Error", e);
            return "{\"error\":\"JSON_ERROR\"}";
        }
    }
    
    private String handleError(String toolName, Exception e) {
        log.error("❌ CRITICAL ERROR in tool [{}]: {}", toolName, e.getMessage(), e);
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("status", "FATAL_ERROR");
        errorResponse.put("message", "System failure in " + toolName + ". " + e.getMessage());
        return toJson(errorResponse);
    }
}
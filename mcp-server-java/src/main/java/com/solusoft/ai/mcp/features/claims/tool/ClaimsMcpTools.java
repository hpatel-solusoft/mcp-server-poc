package com.solusoft.ai.mcp.features.claims.tool;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.apache.tika.Tika;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.security.access.prepost.PreAuthorize;
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
    
    private final Tika tika = new Tika();
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
        "application/pdf", 
        "image/tiff"
    );
    
    public ClaimsMcpTools(ClaimRepository claimRepository, Case360Client case360Client, ObjectMapper objectMapper) {
        this.case360Client = case360Client;
        this.objectMapper = objectMapper;
        this.claimRepository = claimRepository;
    }

    
    @McpTool(name = "extract_claim_info", description = "Extracts claim fields from raw document text. Returns JSON.")
    @PreAuthorize("hasRole('CLAIMS_PROCESSOR')")
    public String extractClaimInfo(String documentText) {
        log.info("[TOOL] Entering extract_claim_info");
        
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
            
            String claimType = (lowerText.contains("vehicle") || lowerText.contains("car")) ? "auto" : "healthcare";
            claimData.put("claim_type", claimType);
            
            String result = toJson(claimData);
            log.debug("Return value (JSON): {}", result);
            log.info("[TOOL] Exiting extract_claim_info");
            return result;
            
        } catch (Exception e) {
            return handleError("extract_claim_info", e);
        }
    }
    
    @McpTool(description = "Uploads a base64 encoded document to Case360")
    @PreAuthorize("hasRole('CLAIMS_PROCESSOR')")
    public String uploadDocument(String documentBase64, String documentName) {
        log.info("[TOOL] Entering upload_document");
        
        try {
            if (documentBase64 == null || documentBase64.isEmpty()) {
                throw new IllegalArgumentException("Base64 string is empty.");
            }

            if (documentBase64.contains(",")) {
                documentBase64 = documentBase64.substring(documentBase64.indexOf(",") + 1);
            }

            documentBase64 = documentBase64.replaceAll("\\s+", "");
            
            byte[] docBytes = Base64.getDecoder().decode(documentBase64);
            log.debug("✓ Decoded {} KB of data.", docBytes.length / 1024);
            
            String detectedType = tika.detect(docBytes);
            log.debug("Detected MIME type: {}", detectedType);
            if (!ALLOWED_MIME_TYPES.contains(detectedType)) {
                throw new SecurityException("Security Block: File type '" + detectedType + "' is not allowed.");
            }
            
            String safeExtension = "";
            if (documentName != null && documentName.contains(".")) {
                safeExtension = documentName.substring(documentName.lastIndexOf("."));
            }
            String safeFileName = UUID.randomUUID().toString() + safeExtension;
            

            BigDecimal templateId = case360Client.getFilestoreTemplateId("Claim Document");
            String documentId = case360Client.createFileStore(templateId);
            
            case360Client.uploadDocument(new BigDecimal(documentId), docBytes, safeFileName);
            
            log.info("✓ Document uploaded successfully to Case360 with ID: {}", documentId);
            log.info("[TOOL] Exiting upload_document");
            return toJson(Map.of("success", true, "document_id", documentId,"stored_name", safeFileName));
            
        } catch (Exception e) {
            log.error("❌ Base64 Decoding/Upload Failed.", e);
            return handleError("upload_document", e);
        }
    }
    

    @McpTool(
        name = "create_motor_claim", 
        description = "Creates a Motor Insurance Claim. Requires vehicle and accident details."
    )
    @PreAuthorize("hasRole('CLAIMS_PROCESSOR')")
    public String createMotorClaim(CreateMotorClaimRequest request) { 
        log.info("[TOOL] Entering create_motor_claim");
        try {
            String claimId = String.valueOf(System.currentTimeMillis());
            claimId = "AUTO-"+claimId;
            @SuppressWarnings("unchecked")
            Map<String, Object> fieldsMap = objectMapper.convertValue(request, Map.class);
            log.info("Converted Request to Map: {}", fieldsMap);

            // 3. BACKEND: Use existing generic logic
            BigDecimal templateId = case360Client.getCaseFolderTemplateId("Motor Claim");
            String caseId = case360Client.createCase(templateId);
            
            Map<String, Object> updates = normalizeDataForBackend(fieldsMap);
            
            updates.put("CREATED_ON", Instant.now());
            updates.put("CLAIM_ID", claimId);
            updates.put("CLAIM_STATUS", "reported");
            case360Client.updateCaseFields(caseId, updates);

            // --- CHANGED: Construct structured JSON response ---
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("case_id", caseId);
            response.put("claim_id", claimId);
            response.put("claim_doc_id", request.claimDocId());
            response.put("processed_at", Instant.now().toString());

            String result = toJson(response);
            log.debug("Return value: {}", result);
            log.info("[TOOL] Exiting create_motor_claim");
            return result;

        } catch (Exception e) {
            log.error("❌ create_motor_claim Failed.", e);
            return handleError("create_motor_claim", e);
        }
    }
    
    @McpTool(
        name = "create_healthcare_claim", 
        description = "Creates a Healthcare/Medical Claim. Requires diagnosis and hospital details."
    )
    @PreAuthorize("hasRole('CLAIMS_PROCESSOR')")
    public String createHealthClaim(CreateHealthClaimRequest request) { 
        log.info("[TOOL] Entering create_healthcare_claim");
        try {
            String claimId = String.valueOf(System.currentTimeMillis());
            claimId = "HC-"+claimId;
            @SuppressWarnings("unchecked")
            Map<String, Object> fieldsMap = objectMapper.convertValue(request, Map.class);
            log.info("Converted Request to Map: {}", fieldsMap);

            // 3. BACKEND: Use existing generic logic
            BigDecimal templateId = case360Client.getCaseFolderTemplateId("Healthcare Claim");
            String caseId = case360Client.createCase(templateId);
            
            Map<String, Object> updates = normalizeDataForBackend(fieldsMap);
            
            updates.put("CREATED_ON", Instant.now());
            updates.put("CLAIM_ID", claimId);
            updates.put("CLAIM_STATUS", "reported");
            case360Client.updateCaseFields(caseId, updates);

            // --- CHANGED: Construct structured JSON response ---
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("case_id", caseId);
            response.put("claim_id", claimId);
            response.put("claim_doc_id", request.claimDocId());
            response.put("processed_at", Instant.now().toString());

            String result = toJson(response);
            log.debug("Return value: {}", result);
            log.info("[TOOL] Exiting create_healthcare_claim");
            return result;

        } catch (Exception e) {
            log.error("❌ create_healthcare_claim Failed.", e);
            return handleError("create_healthcare_claim", e);
        }
    }
    
    @McpTool(name="store_claim_record",
    		description = "PRIMARY ACTION: Persist a finalized insurance claim to the database. " +
                    "Call this whenever a user asks to save, process, or store a claim document. " +
                    "Automatically extracts relevant business data (like diagnosis, vehicle info, dates) " +
                    "from the context and puts it into the dynamic 'claimDetails' field.")
    @PreAuthorize("hasRole('CLAIMS_PROCESSOR')")
    public String storeClaimRecord(StoreClaimRequest request) {
        log.info("[TOOL] Entering store_claim_record");
        try {

        	Map<String, Object> claimDetails ;
        	try {
                // Manually parse the JSON string the AI sent
                if (request.additionalClaimsFields() != null && !request.additionalClaimsFields().isBlank()) {
                	Map<String, Object> rawMap = new ObjectMapper().readValue(request.additionalClaimsFields(), Map.class);
                    
                    rawMap.keySet().removeIf(key -> 
                        key.toLowerCase().contains("admin") || 
                        key.toLowerCase().contains("role") ||
                        key.toLowerCase().contains("permission")
                    );
                    
                    claimDetails = rawMap;
                } else {
                	claimDetails = new HashMap<>();
                }
            } catch (JsonProcessingException e) {
                log.warn("AI sent bad JSON: " + request.additionalClaimsFields());
                claimDetails = new HashMap<>();
            }

            String jsonBlob = toJson(claimDetails); // Helper method to convert Map -> JSON String
    
            Optional<Claim> existing = claimRepository.findByClaimId(request.claimId());
    
            // 3. Determine the DB Primary Key (ID)
            Integer dbId = existing.map(Claim::id).orElse(null);
    
            // 4. Create the Entity (Record)
            Claim claimEntity = new Claim(
                dbId, 
                request.claimId(),
                request.claimDocId(),
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

            // --- CHANGED: Added "processed_at" timestamp to response ---
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("status", "success");
            response.put("claim_id", request.claimId());
            response.put("action", (dbId == null ? "created" : "updated"));

            String result = toJson(response);
            log.info("[TOOL] Exiting store_claim_record");
            log.debug("Return value: {}", result);
            return result;

        } catch (Exception e) {
            log.error("❌ store_claim_record Failed.", e);
            e.printStackTrace();
            return handleError("store_claim_record", e);
        }
    }
    
    @McpTool(name = "get_claim_status", description = "Retrieves the current status of a claim by its ID")
    @PreAuthorize("hasAnyRole('CLAIMS_PROCESSOR', 'SUPPORT_VIEWER')") 
    public String getClaimStatus(String claimId) {
    	log.info("[TOOL] Entering get_claim_status");
        try {
            String claimStatus = case360Client.getClaimStatus(claimId);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("claim_id", claimId);
            response.put("claim_status", claimStatus==null ? "unknown" : claimStatus);

            String result = toJson(response);
            log.debug("Return value: {}", result);
            log.info("[TOOL] Exiting get_claim_status");
            return result;

        } catch (Exception e) {
            log.error("❌ get_claim_status Failed.", e);
            return handleError("get_claim_status", e);
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
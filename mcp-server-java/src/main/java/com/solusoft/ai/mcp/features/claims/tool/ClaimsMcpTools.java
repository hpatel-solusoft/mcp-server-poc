package com.solusoft.ai.mcp.features.claims.tool;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.tika.Tika;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solusoft.ai.mcp.features.claims.model.CreateHealthClaimRequest;
import com.solusoft.ai.mcp.features.claims.model.CreateMotorClaimRequest;
import com.solusoft.ai.mcp.features.claims.model.StoreClaimRequest;
import com.solusoft.ai.mcp.features.claims.service.ClaimPersistenceService;
import com.solusoft.ai.mcp.integration.case360.Case360Client;
import com.solusoft.ai.mcp.integration.case360.Case360RecoveryHandler;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ClaimsMcpTools {

    private final Case360Client case360Client;
    private final ObjectMapper objectMapper;
    private final Case360RecoveryHandler recoveryHandler;
    private final ClaimPersistenceService persistenceService;
    
    private final Tika tika = new Tika();
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
        "application/pdf", 
        "image/tiff"
    );
    
    public ClaimsMcpTools(Case360Client case360Client, 
                         ObjectMapper objectMapper,
                         Case360RecoveryHandler recoveryHandler,
                         ClaimPersistenceService persistenceService) {
        this.case360Client = case360Client;
        this.objectMapper = objectMapper;
        this.recoveryHandler = recoveryHandler;
        this.persistenceService = persistenceService;
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
    
    @McpTool(name="upload_document", description = "Uploads a base64 encoded document to Case360")
    @PreAuthorize("hasRole('CLAIMS_PROCESSOR')")
    public String uploadDocument(String documentBase64, String documentName) {
        log.info("[TOOL] Entering upload_document");
        String documentId = null;
        BigDecimal templateId = null;
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

            templateId = case360Client.getFilestoreTemplateId("Claim Document");
            documentId = case360Client.createFileStore(templateId);
            
            // If uploadDocument fails, the previously created documentId is registered for cleanup
            case360Client.uploadDocument(new BigDecimal(documentId), docBytes, safeFileName);
            
            log.info("✓ Document uploaded successfully to Case360 with ID: {}", documentId);
            log.info("[TOOL] Exiting upload_document");
            return toJson(Map.of("success", true, "document_id", documentId,"stored_name", safeFileName));
            
        } catch (Exception e) {
            log.error("❌ upload_document Failed.", e);
            if (documentId != null) {
                recoveryHandler.registerForAsyncCleanup(documentId,  3, "Upload failed after creation");
            }
            return handleError("upload_document", e);
        }
    }

    @McpTool(
        name = "create_motor_claim", 
        description = "Creates a Motor Insurance Claim.Use the documentId from the upload_document tool as claimdDocId. Requires vehicle and accident details."
    )
    @PreAuthorize("hasRole('CLAIMS_PROCESSOR')")
    public String createMotorClaim(CreateMotorClaimRequest request) { 
        log.info("[TOOL] Entering create_motor_claim");
        String caseId = null;
        BigDecimal templateId = null;
        try {
            String claimId = "AUTO-" + System.currentTimeMillis();
            @SuppressWarnings("unchecked")
            Map<String, Object> fieldsMap = objectMapper.convertValue(request, Map.class);
            log.info("Converted Request to Map: {}", fieldsMap);

            templateId = case360Client.getCaseFolderTemplateId("Motor Claim");
            caseId = case360Client.createCase(templateId);
            
            Map<String, Object> updates = normalizeDataForBackend(fieldsMap);
            updates.put("CREATED_ON", Instant.now());
            updates.put("CLAIM_ID", claimId);
            updates.put("CLAIM_STATUS", "reported");
            
            // If updateCaseFields fails, the previously created caseId is registered for cleanup
            case360Client.updateCaseFields(caseId, updates);

            String result = toJson(Map.of("status", "success", "case_id", caseId, "claim_id", claimId, "claim_doc_id", request.claimDocId(), "processed_at", Instant.now().toString()));
            log.debug("Return value: {}", result);
            log.info("[TOOL] Exiting create_motor_claim");
            return result;

        } catch (Exception e) {
            log.error("❌ create_motor_claim Failed.", e);
            if (caseId != null) {
                recoveryHandler.registerForAsyncCleanup(caseId, 1, "Field update failed after case creation");
            }
            if (request.claimDocId() != null) recoveryHandler.registerForAsyncCleanup(request.claimDocId(), 3, "Case creation failed");
            return handleError("create_motor_claim", e);
        }
    }
    
    @McpTool(
        name = "create_healthcare_claim", 
        description = "Creates a Healthcare/Medical Claim. Use the documentId from the upload_document tool as claimdDocId. Requires diagnosis and hospital details."
    )
    @PreAuthorize("hasRole('CLAIMS_PROCESSOR')")
    public String createHealthClaim(CreateHealthClaimRequest request) { 
        log.info("[TOOL] Entering create_healthcare_claim");
        String caseId = null;
        BigDecimal templateId = null;
        try {
            String claimId = "HC-" + System.currentTimeMillis();
            @SuppressWarnings("unchecked")
            Map<String, Object> fieldsMap = objectMapper.convertValue(request, Map.class);
            log.info("Converted Request to Map: {}", fieldsMap);

            templateId = case360Client.getCaseFolderTemplateId("Healthcare Claim");
            caseId = case360Client.createCase(templateId);
            
            Map<String, Object> updates = normalizeDataForBackend(fieldsMap);
            updates.put("CREATED_ON", Instant.now());
            updates.put("CLAIM_ID", claimId);
            updates.put("CLAIM_STATUS", "reported");
            
            // If updateCaseFields fails, caseId is registered for cleanup
            case360Client.updateCaseFields(caseId, updates);

            String result = toJson(Map.of("status", "success", "case_id", caseId, "claim_id", claimId, "claim_doc_id", request.claimDocId(), "processed_at", Instant.now().toString()));
            log.debug("Return value: {}", result);
            log.info("[TOOL] Exiting create_healthcare_claim");
            return result;

        } catch (Exception e) {
            log.error("❌ create_healthcare_claim Failed.", e);
            if (caseId != null) {
                recoveryHandler.registerForAsyncCleanup(caseId,  1, "Field update failed after case creation");
            }
            if (request.claimDocId() != null) recoveryHandler.registerForAsyncCleanup(request.claimDocId(),  3, "Case creation failed");
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
            // Delegation to service layer ensures resilience (Deferred sync if DB is down)
            ClaimPersistenceService.PersistenceResult result = persistenceService.saveClaimWithResilience(request);

            Map<String, Object> response = new HashMap<>();
            response.put("success", result.success());
            response.put("status", result.status());
            response.put("claim_id", request.claimId());
            response.put("action", result.action());
            if (result.message() != null) response.put("message", result.message());

            String resultJson = toJson(Map.of("success", result.success(), "status", result.status(), "claim_id", request.claimId(), "action", result.action(), "message", result.message()));
            log.info("[TOOL] Exiting store_claim_record");
            log.debug("Return value: {}", resultJson);
            return resultJson;

        } catch (Exception e) {
            log.error("❌ store_claim_record Failed.", e);
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
            response.put("claim_status", claimStatus == null ? "unknown" : claimStatus);

            String result = toJson(response);
            log.debug("Return value: {}", result);
            log.info("[TOOL] Exiting get_claim_status");
            return result;
        } catch (Exception e) {
            log.error("❌ get_claim_status Failed.", e);
            return handleError("get_claim_status", e);
        }
    }

    private Map<String, Object> normalizeDataForBackend(Map<String, Object> input) {
        log.info("Normalizing data for backend. Input keys: {}", input != null ? input.keySet() : "null");
        if (input == null) return new HashMap<>();
        Map<String, Object> output = new HashMap<>();
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            String cleanKey = toSnakeCaseUpper(entry.getKey());
            Object value = entry.getValue();
            if (cleanKey.contains("DATE") && value instanceof String) {
                try { output.put(cleanKey, LocalDate.parse((String) value)); } 
                catch (Exception e) { output.put(cleanKey, value); }
            } else { output.put(cleanKey, value); }
        }
        return output;
    }

    private String toSnakeCaseUpper(String str) {
        String regex = "([a-z])([A-Z]+)";
        String replacement = "$1_$2";
        return str.replaceAll(regex, replacement).replace(" ", "_").replace("-", "_").toUpperCase();
    }

    private String toJson(Object data) {
        try { return objectMapper.writeValueAsString(data); } 
        catch (JsonProcessingException e) {
            log.error("JSON Serialization Error", e);
            return "{\"error\":\"JSON_ERROR\"}";
        }
    }

    private String handleError(String toolName, Exception e) {
        log.error("❌ TOOL_FAILURE [tool={}] [error_type={}]", toolName, e.getClass().getSimpleName(), e);
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("status", "FATAL_ERROR");
        if (e instanceof IllegalArgumentException || e instanceof SecurityException) {
            errorResponse.put("category", "USER_ERROR");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("suggestion", "Review the input arguments and try again.");
        } else {
            errorResponse.put("category", "SYSTEM_ERROR");
            errorResponse.put("message", e.getMessage() != null ? e.getMessage() : "An unexpected system error occurred.");
            errorResponse.put("suggestion", "Do not retry. Report this error code.");
        }
        return toJson(errorResponse);
    }
}
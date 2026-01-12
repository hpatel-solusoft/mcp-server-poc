package com.solusoft.ai.mcp.features.claims.tool;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solusoft.ai.mcp.features.claims.model.CreateHealthClaimRequest;
import com.solusoft.ai.mcp.features.claims.model.CreateMotorClaimRequest;
import com.solusoft.ai.mcp.integration.case360.Case360Client;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ClaimsMcpTools {

    private final JdbcTemplate jdbcTemplate;
    private final Case360Client case360Client;
    private final ObjectMapper objectMapper;
    
    public ClaimsMcpTools(JdbcTemplate jdbcTemplate, Case360Client case360Client, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.case360Client = case360Client;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
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
                    workflow_id TEXT,
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
    }
    
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
            log.info("[TOOL] Exiting extract_claim_info");
            return result;
            
        } catch (Exception e) {
            return handleError("extractClaimInfo", e);
        }
    }
    
    @McpTool(description = "Uploads a base64 encoded document to Case360")
    public String uploadDocument(String documentBase64, String documentName) {
        log.info("[TOOL] Entering upload_document");
        // Log length instead of full content for base64 to avoid flooding logs, but keeping 'documentName' visible
        log.debug("Input documentName: {}, base64Length: {}", documentName, (documentBase64 != null ? documentBase64.length() : 0));
        
        try {
            if (documentBase64 == null || documentBase64.length() < 100) {
                throw new IllegalArgumentException("Base64 string is too short. Injection failed?");
            }

            // --- 1. CLEANUP ---
            // Remove Data URI prefix if present (e.g., "data:application/pdf;base64,")
            if (documentBase64.contains(",")) {
                documentBase64 = documentBase64.substring(documentBase64.indexOf(",") + 1);
            }
            
            // --- 2. DECODE ---
            // Use MimeDecoder to handle newlines/whitespace safely
            byte[] docBytes = Base64.getMimeDecoder().decode(documentBase64);
            
            log.info("✓ Decoded {} KB of data.", docBytes.length / 1024);

            // --- 3. UPLOAD TO CASE360 ---
            // Create the FileStore object first
            BigDecimal templateId = case360Client.getFilestoreTemplateId("Claim Document");
            String documentId = case360Client.createFileStore(templateId);
            
            // Upload the actual binary content
            case360Client.uploadDocument(new BigDecimal(documentId), docBytes, documentName);
            
            log.info("✓ Document uploaded successfully to Case360 with ID: {}", documentId);
            String result = toJson(Map.of("success", true, "document_id", documentId));
            
            log.debug("Return value: {}", result);
            log.info("[TOOL] Exiting upload_document");
            return result;
            
        } catch (IllegalArgumentException e) {
            log.error("❌ Base64 Decoding Failed.", e);
            return handleError("uploadDocument", e);
        } catch (Exception e) {
            return handleError("uploadDocument", e);
        }
    }
    
    @McpTool(name = "create_motor_claim", description = "Use this tool ONLY for vehicle, car, or motor accident claims. Do not use for medical bills. Creates a new Motor insurance claim workflow in Case360.")
    public String createMotorClaim(CreateMotorClaimRequest request) {
        log.info("[TOOL] Entering create_motor_claim");
        log.debug("Input request: {}", request);
        
        try {
            Instant now = Instant.now();
            
            LocalDate localDate = LocalDate.parse(request.incidentDate());
            
            BigDecimal templateId = case360Client.getCaseFolderTemplateId("Motor Claim");
            String caseId = case360Client.createCase(templateId);
            
            Map<String, Object> updates = new HashMap<>();
            updates.put("CLAIMANT_NAME", request.claimantName());
            updates.put("POLICY_NUM", request.policyNumber());
            updates.put("CLAIM_AMOUNT", request.claimAmount());
            updates.put("CLAIM_DESCRIPTION", request.description());
            updates.put("INCIDENT_DATE", localDate);
            updates.put("CREATED_ON", now); // Ensure Case360Client handles Date/Instant
            updates.put("CLAIM_ID", now.toEpochMilli()); 
            // Handle Optionals safely
            if (request.incidentType() != null) updates.put("INCIDENT_TYPE", request.incidentType());
            if (request.priority() != null) updates.put("PRIORITY", request.priority()); 
            if (request.claimDocId() != null) updates.put("CLAIM_DOC_ID", request.claimDocId());

            case360Client.updateCaseFields(caseId, updates);

            String result = String.format("SUCCESS: Motor Claim created. Case ID: %s. Status: PROCESSING.", caseId);
            
            log.debug("Return value: {}", result);
            log.info("[TOOL] Exiting create_motor_claim");
            return result;

        } catch (Exception e) {
            return handleError("createMotorClaim", e);
        }
    }
    
    @McpTool(name = "create_healthcare_claim", description = "Use this tool ONLY for medical, hospital, doctor, or healthcare related claims. Do not use for vehicle accidents. Creates a new Healthcare insurance claim workflow in Case360.")
    public String createHealthClaim(CreateHealthClaimRequest request) {
        log.info("[TOOL] Entering create_healthcare_claim");
        log.debug("Input request: {}", request);
        
        try {
            Instant now = Instant.now();
            LocalDate localDate = LocalDate.parse(request.incidentDate());
            BigDecimal templateId = case360Client.getCaseFolderTemplateId("Healthcare Claim");
            String caseId = case360Client.createCase(templateId);
            
            Map<String, Object> updates = new HashMap<>();
            updates.put("CLAIMANT_NAME", request.claimantName());
            updates.put("POLICY_NUM", request.policyNumber());
            updates.put("CLAIM_AMOUNT", request.claimAmount());
            updates.put("CLAIM_DESCRIPTION", request.description());
            updates.put("INCIDENT_DATE", localDate);
            updates.put("CREATED_ON", now);
            updates.put("DIAGNOSIS", request.diagnosis());
            updates.put("HOSPITAL", request.hospitalName());
            updates.put("PHYSICIAN", request.physician());
            updates.put("PHYSICIAN_NOTES", request.physicianNotes());
            updates.put("TREATMENT_SUMMARY", request.treatmentSummary());
            updates.put("CLAIM_ID", now.toEpochMilli()); 
            
            if (request.incidentType() != null) updates.put("INCIDENT_TYPE", request.incidentType());
            if (request.priority() != null) updates.put("PRIORITY", request.priority()); 
            if (request.claimDocId() != null) updates.put("CLAIM_DOC_ID", request.claimDocId());

            case360Client.updateCaseFields(caseId, updates);

            String result = String.format("SUCCESS: Healthcare Claim created. Case ID: %s. Status: PROCESSING.", caseId);
            
            log.debug("Return value: {}", result);
            log.info("[TOOL] Exiting create_healthcare_claim");
            return result;

        } catch (Exception e) {
            return handleError("createHealthClaim", e);
        }
    }
    
    @McpTool(name="store_claim_record", description = "Saves the claim and workflow result to the local database")
    public String storeClaimRecord(String claimDataJson, String workflowResultJson) {
        log.info("[TOOL] Entering store_claim_record");
        log.debug("Input claimDataJson: {}", claimDataJson);
        log.debug("Input workflowResultJson: {}", workflowResultJson);
        
        try {
            Map<String, Object> claim = objectMapper.readValue(claimDataJson, Map.class);
            Map<String, Object> workflow = objectMapper.readValue(workflowResultJson, Map.class);

            String claimId = (String) claim.getOrDefault("claim_id", 
                                             workflow.getOrDefault("claim_id", "CLM-" + System.currentTimeMillis()));

            String workflowId = workflow.containsKey("workflow_id") ? 
                                String.valueOf(workflow.get("workflow_id")) : null;
            String caseId = workflow.containsKey("case_id") ? 
                            String.valueOf(workflow.get("case_id")) : null;

            // Simplified SQL for readability
            String sql = "INSERT OR REPLACE INTO claims (claim_id, policy_number, claimant_name, " + 
                         "claim_type, claim_amount, diagnosis, incident_date, hospital, physician, " +
                         "vehicle_make, vehicle_model, vehicle_year, incident_type, workflow_id, " + 
                         "case_id, status, created_at, processed_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

            jdbcTemplate.update(sql,
                claimId, claim.get("policy_number"), claim.get("claimant_name"), claim.get("claim_type"),
                claim.get("claim_amount"), claim.get("diagnosis"), claim.get("incident_date"),
                claim.get("hospital"), claim.get("physician"), claim.get("vehicle_make"),
                claim.get("vehicle_model"), claim.get("vehicle_year"), claim.get("incident_type"),
                workflowId, caseId, "submitted_to_case360", Instant.now().toString(), Instant.now().toString()
            );

            log.info("✓ Saved Claim {} to database", claimId);
            String result = toJson(Map.of("success", true, "claim_id", claimId, "status", "saved_to_db"));
            
            log.debug("Return value: {}", result);
            log.info("[TOOL] Exiting store_claim_record");
            return result;

        } catch (Exception e) {
            return handleError("storeClaimRecord", e);
        }
    }
    
    // --- Helper Methods ---

    private String toJson(Object data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            log.error("JSON Serialization Error", e);
            return "{\"error\":\"JSON_ERROR\"}";
        }
    }
    
    /**
     * Centralized Error Handling.
     * Logs the stack trace for the developer.
     * Returns a clear 'STOP' signal to the AI Model.
     */
    private String handleError(String toolName, Exception e) {
        // 1. Log the full stack trace on the Server side (so you can see it!)
        log.error("❌ CRITICAL ERROR in tool [{}]: {}", toolName, e.getMessage(), e);
        
        // 2. Return a message that discourages the AI from retrying blindly
        // Using "STOP_SEQUENCE" or "FATAL" helps prompt engineering.
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("status", "FATAL_ERROR");
        errorResponse.put("message", "System failure in " + toolName + ". " + e.getMessage());
        errorResponse.put("instruction", "Do not retry. Report this technical error to the user immediately.");
        
        return toJson(errorResponse);
    }
    
    @McpTool(name = "tool_test", description = "testing tools call")
    public void testTool() {
       log.info("[TOOL] Entering tool_test");
       log.info("Testing Tool Executed");
       log.info("[TOOL] Exiting tool_test");
    }
}
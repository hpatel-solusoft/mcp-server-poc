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
            // Note: This table has fixed columns. If you want to store the extra "dynamic" fields
            // locally, you might need to add a JSON column (e.g., 'additional_data TEXT') in the future.
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
        log.debug("Input documentName: {}, base64Length: {}", documentName, (documentBase64 != null ? documentBase64.length() : 0));
        
        try {
            if (documentBase64 == null || documentBase64.length() < 100) {
                throw new IllegalArgumentException("Base64 string is too short. Injection failed?");
            }

            if (documentBase64.contains(",")) {
                documentBase64 = documentBase64.substring(documentBase64.indexOf(",") + 1);
            }
            
            byte[] docBytes = Base64.getMimeDecoder().decode(documentBase64);
            log.info("✓ Decoded {} KB of data.", docBytes.length / 1024);

            BigDecimal templateId = case360Client.getFilestoreTemplateId("Claim Document");
            String documentId = case360Client.createFileStore(templateId);
            
            case360Client.uploadDocument(new BigDecimal(documentId), docBytes, documentName);
            
            log.info("✓ Document uploaded successfully to Case360 with ID: {}", documentId);
            String result = toJson(Map.of("success", true, "document_id", documentId));
            
            return result;
            
        } catch (Exception e) {
            log.error("❌ Base64 Decoding/Upload Failed.", e);
            return handleError("uploadDocument", e);
        }
    }
    
    @McpTool(
        name = "create_motor_claim", 
        description = "Creates a Motor Insurance Claim workflow. Use this ONLY if the document is related to a car, vehicle, or accident. " +
                      "Input must be a JSON object containing ALL fields found in the document " +
                      "(e.g., policy_number, vehicle_make, accident_location, third_party_driver, etc.)."
    )
    public String createMotorClaim(Map<String, Object> dynamicData) {
        log.info("[TOOL] Entering create_motor_claim ");
        log.debug("Raw Input: {}", dynamicData);
        
        try {
            // 1. Create Case in Case360
            BigDecimal templateId = case360Client.getCaseFolderTemplateId("Motor Claim");
            String caseId = case360Client.createCase(templateId);
            
            // 2. Normalize Keys (e.g. "vehicleModel" -> "VEHICLE_MODEL")
            Map<String, Object> updates = normalizeDataForBackend(dynamicData);
            
            // 3. Add Workflow Defaults
            updates.put("CREATED_ON", Instant.now());
            updates.put("CLAIM_ID", System.currentTimeMillis());
            
            // 4. Send All Fields to Backend
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
        description = "Creates a Healthcare/Medical Claim workflow. Use this ONLY for medical, hospital, doctor, or healthcare related claims. " +
                      "Input must be a JSON object containing ALL fields found in the document " +
                      "(e.g., claimant_name, diagnosis, hospital_name, physician, treatment_cost, etc.)."
    )
    public String createHealthClaim(Map<String, Object> dynamicData) {
        log.info("[TOOL] Entering create_healthcare_claim ");
        log.debug("Raw Input: {}", dynamicData);
        
        try {
            // 1. Create Case in Case360
            BigDecimal templateId = case360Client.getCaseFolderTemplateId("Healthcare Claim");
            String caseId = case360Client.createCase(templateId);
            
            // 2. Normalize Keys (e.g. "physicianNotes" -> "PHYSICIAN_NOTES")
            Map<String, Object> updates = normalizeDataForBackend(dynamicData);
            
            // 3. Add Workflow Defaults
            updates.put("CREATED_ON", Instant.now());
            updates.put("CLAIM_ID", System.currentTimeMillis());
            
            // 4. Send All Fields to Backend
            case360Client.updateCaseFields(caseId, updates);

            String result = String.format("SUCCESS: Healthcare Claim created. Case ID: %s. Fields processed: %d.", caseId, updates.size());
            log.info("[TOOL] Exiting create_healthcare_claim");
            return result;

        } catch (Exception e) {
            log.error("❌ create_healthcare_claim Failed.", e);
            return handleError("createHealthClaim", e);
        }
    }
    
    @McpTool(name="store_claim_record", description = "Saves the claim and workflow result to the local database")
    public String storeClaimRecord(String claimDataJson, String workflowResultJson) {
        log.info("[TOOL] Entering store_claim_record");
        
        try {
            Map<String, Object> claim = objectMapper.readValue(claimDataJson, Map.class);
            Map<String, Object> workflow = objectMapper.readValue(workflowResultJson, Map.class);

            String claimId = (String) claim.getOrDefault("claim_id", 
                                             workflow.getOrDefault("claim_id", "CLM-" + System.currentTimeMillis()));

            String workflowId = workflow.containsKey("workflow_id") ? 
                                String.valueOf(workflow.get("workflow_id")) : null;
            String caseId = workflow.containsKey("case_id") ? 
                            String.valueOf(workflow.get("case_id")) : null;

            // Note: This SQL only inserts known columns. Dynamic fields not listed here will 
            // be saved to Case360 (via the tools above) but NOT to this local SQLite table 
            // unless you update the schema.
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
            return toJson(Map.of("success", true, "claim_id", claimId, "status", "saved_to_db"));

        } catch (Exception e) {
            log.error("❌ store_claim_record Failed.", e);
            return handleError("storeClaimRecord", e);
        }
    }
    
    // -------------------------------------------------------------------------
    //  HELPER METHODS (Low-Code Key Normalization)
    // -------------------------------------------------------------------------

    /**
     * Converts incoming JSON map keys (camelCase or spaces) to Database/Case360 standard (UPPER_SNAKE_CASE).
     * Example: "vehicleModel" -> "VEHICLE_MODEL", "Policy Number" -> "POLICY_NUMBER"
     */
    private Map<String, Object> normalizeDataForBackend(Map<String, Object> input) {
        Map<String, Object> output = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            String rawKey = entry.getKey();
            Object value = entry.getValue();

            // 1. Transform Key
            String cleanKey = toSnakeCaseUpper(rawKey);
            
            // 2. Handle Dates safely
            // If the key suggests a date and the value is a string, try to parse it.
            // If parsing fails (e.g., "Yesterday"), we send the raw string to let Case360 handle it.
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
        // Regex to insert underscore before capital letters (camelCase -> camel_Case)
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
    
    @McpTool(name = "tool_test", description = "testing tools call")
    public void testTool() {
       log.info("[TOOL] Entering tool_test");
       log.info("Testing Tool Executed");
       log.info("[TOOL] Exiting tool_test");
    }
}
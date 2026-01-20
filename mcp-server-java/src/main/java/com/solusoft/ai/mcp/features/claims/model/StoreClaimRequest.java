package com.solusoft.ai.mcp.features.claims.model;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

// Only include non-null fields in the JSON schema generated for the LLM
@JsonClassDescription("Flat arguments for storing a claim.")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StoreClaimRequest(
    
    String claimId,
    String claimDocId,
    String policyNumber,
    String claimantName,
    String claimType, // Expected: "MOTOR" or "HEALTH"
    BigDecimal claimAmount,
    String caseId,
    /*String vehicleMake,
    String incidentType, // e.g., "Collision", "Theft"
    String diagnosis,
    String hospital,
    String physician,*/
    

    // Shared / Common Dynamic Fields
    @JsonFormat(pattern = "yyyy-MM-dd")  
    LocalDate incidentDate,
    
    //Map<String, Object> claimDetails
    @JsonPropertyDescription(
            "A raw JSON string containing ALL other detected fields from the document. " +
            "Examples: {\"vehicle_make\": \"Ford\", \"incident_time\": \"10:30 AM\", \"hospital\": \"General\"}. " +
            "Extract as many factual key-value pairs as possible."
        )
    String additionalClaimsFields
) {}
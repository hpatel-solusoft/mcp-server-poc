package com.solusoft.ai.mcp.features.claims.model;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.math.BigDecimal;
import java.time.LocalDate;

// Only include non-null fields in the JSON schema generated for the LLM
@JsonClassDescription("Flat arguments for storing a claim.")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StoreClaimRequest(
    
    String claimId,
    String policyNumber,
    String claimantName,
    String claimType, // Expected: "MOTOR" or "HEALTH"
    BigDecimal claimAmount,
    String caseId,
    String vehicleMake,
    String incidentType, // e.g., "Collision", "Theft"
    String diagnosis,
    String hospital,
    String physician,

    // Shared / Common Dynamic Fields
    @JsonFormat(pattern = "yyyy-MM-dd")  
    LocalDate incidentDate
) {}
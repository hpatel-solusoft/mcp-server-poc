package com.solusoft.ai.mcp.features.claims.model;
import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("Flat arguments for storing a claim.")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StoreClaimRequest(
    
    String claimId,
    String claimDocId,
    String policyNumber,
    String claimantName,
    String claimType, 
    BigDecimal claimAmount,
    String caseId,

    @JsonFormat(pattern = "yyyy-MM-dd")  
    LocalDate incidentDate,

    @JsonPropertyDescription(
            "A raw JSON string containing ALL other detected fields from the document. " +
            "Examples: {\"vehicle_make\": \"Ford\", \"incident_time\": \"10:30 AM\", \"hospital\": \"General\"}. " +
            "Extract as many factual key-value pairs as possible."
        )
    String additionalClaimsFields
) {}
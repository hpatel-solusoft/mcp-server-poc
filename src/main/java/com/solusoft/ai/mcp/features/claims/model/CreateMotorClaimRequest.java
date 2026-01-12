package com.solusoft.ai.mcp.features.claims.model;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

// 1. Motor Claim Input
@JsonClassDescription("Details required to file a new motor/vehicle insurance claim")
public record CreateMotorClaimRequest(
    
    @JsonPropertyDescription("The full name of the policyholder filing the claim")
    String claimantName,

    @JsonPropertyDescription("The 10-character policy number (e.g., POL-123456)")
    String policyNumber,

    @JsonPropertyDescription("The estimated cost of damage or amount claimed")
    Double claimAmount,

    @JsonPropertyDescription("The date when the accident occurred (YYYY-MM-DD)")
    String incidentDate, // Keeping as String is safer for LLMs, we parse it later

    @JsonPropertyDescription("A brief description of the accident and damage")
    String description,
    
    @JsonPropertyDescription("Optional: The ID of an uploaded document supporting this claim")
    String claimDocId,
    
    @JsonPropertyDescription("Optional:Type of the Incident")
    String incidentType,
    
    @JsonPropertyDescription("Optional: Priority for the claim")
    String priority
    
    
) {}


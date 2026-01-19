package com.solusoft.ai.mcp.features.claims.model;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonClassDescription;

// 2. Health Claim Input (Example of a second domain)
@JsonClassDescription("Details required to file a medical/health insurance claim")
public record CreateHealthClaimRequest(
    String claimantName,
    String policyNumber,
    BigDecimal claimAmount,
    LocalDate incidentDate,
    String diagnosis,
    String hospitalName,
    String description,
    String claimDocId,
    String incidentType,
    String physician,
    String physicianNotes,
    String priority,
    String treatmentSummary
) {}
package com.solusoft.ai.mcp.features.claims.model;
import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("claims")
public record Claim(
    @Id
    Integer id, // Can be null for new inserts
    
    String claimId,
    String claimDocId,
    String policyNumber,
    String claimantName,
    String claimType,
    BigDecimal claimAmount,
    String caseId,
    String status,
    Instant createdAt,
    Instant processedAt,
    String additionalData // Stores the raw JSON string
) {}
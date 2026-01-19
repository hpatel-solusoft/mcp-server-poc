package com.solusoft.ai.mcp.features.claims.model;
public record ClaimDetails(
    String claimId,
    String status,
    String type, // "Auto" or "Health"
    Double amount,
    String description,
    String createdDate
) {}
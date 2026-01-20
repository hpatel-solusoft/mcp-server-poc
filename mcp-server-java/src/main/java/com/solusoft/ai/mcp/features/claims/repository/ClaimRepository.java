package com.solusoft.ai.mcp.features.claims.repository;

import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import com.solusoft.ai.mcp.features.claims.model.Claim;

public interface ClaimRepository extends ListCrudRepository<Claim, Integer> {
    
    // Spring generates this SQL automatically: SELECT * FROM claims WHERE claim_id = ?
    Optional<Claim> findByClaimId(String claimId);
    
    @Modifying
    @Query("""
        INSERT INTO claims (
            claim_id, claim_doc_id, policy_number, claimant_name, claim_type, claim_amount, 
            case_id, status, created_at, processed_at, additional_data
        ) VALUES (
            :#{#c.claimId}, :#{#c.claimId}, :#{#c.policyNumber}, :#{#c.claimantName}, 
            :#{#c.claimType}, :#{#c.claimAmount}, :#{#c.caseId}, 
            :#{#c.status}, :#{#c.createdAt}, :#{#c.processedAt}, 
            :#{#c.additionalData}::jsonb  -- <--- THE CAST
        )
    """)
    void saveClaimWithJson(@Param("c") Claim c);
}
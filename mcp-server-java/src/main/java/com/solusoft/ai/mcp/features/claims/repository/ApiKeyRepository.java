package com.solusoft.ai.mcp.features.claims.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import com.solusoft.ai.mcp.features.claims.model.ApiKeyEntity;

public interface ApiKeyRepository extends CrudRepository<ApiKeyEntity, Long> {

    // Simple, explicit SQL query
    @Query("SELECT * FROM api_keys WHERE key_hash = :hash AND active = true")
    Optional<ApiKeyEntity> findByHash(@Param("hash") String hash);
    
 // Find all active keys for a specific owner
    @Query("SELECT * FROM api_keys WHERE owner = :owner AND active = true")
    List<ApiKeyEntity> findActiveByOwner(@Param("owner") String owner);
}
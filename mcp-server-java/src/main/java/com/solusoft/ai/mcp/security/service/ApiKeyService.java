package com.solusoft.ai.mcp.security.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy; // Critical for self-injection
import org.springframework.stereotype.Service;

import com.solusoft.ai.mcp.features.claims.model.ApiKeyEntity;
import com.solusoft.ai.mcp.features.claims.repository.ApiKeyRepository;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ApiKeyService {

    private final ApiKeyRepository repository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    @Lazy
    private ApiKeyService self; 

    public ApiKeyService(ApiKeyRepository repository) {
        this.repository = repository;
    }

    /**
     * Entry point for Auth Filter.
     * LOGIC: 
     * 1. Hash the incoming raw key.
     * 2. Call the CACHED method using the HASH.
     */
    public ApiKeyEntity validateKey(String plainTextKey) {
        String inputHash = hashKey(plainTextKey);
        // Call 'self' so the Spring Proxy triggers the Cache logic
        return self.getApiKeyByHash(inputHash); 
    }

    /**
     * CACHE POINT: Cache by HASH because 
     * This method is public so the Proxy can see it.
     */
    //@Cacheable(value = "apiKeys", key = "#hash", unless = "#result == null")
    public ApiKeyEntity getApiKeyByHash(String hash) {
        // This is the actual DB hit
        return repository.findByHash(hash).orElse(null);
    }

    public String createApiKey(String owner, String role) {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String plainTextKey = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        ApiKeyEntity entity = new ApiKeyEntity();
        entity.setKeyHash(hashKey(plainTextKey));
        entity.setOwner(owner);
        entity.setRole(role);
        entity.setActive(true); // Ensure new keys are active!
        repository.save(entity);

        return plainTextKey;
    }
    
    /**
     * EVICTION POINT: We now have the power to evict specific keys.
     */
    public int revokeAllExceptLatest(String owner) {
        List<ApiKeyEntity> activeKeys = repository.findActiveByOwner(owner);
        
        if (activeKeys.size() <= 1) {
            return 0; 
        }

        // Sort: Newest first (Descending ID)
        activeKeys.sort((k1, k2) -> k2.getId().compareTo(k1.getId()));

        // Skip the first one (Winner)
        int revokedCount = 0;
        for (int i = 1; i < activeKeys.size(); i++) {
            ApiKeyEntity oldKey = activeKeys.get(i);
            
            // 1. Mark inactive in DB
            oldKey.setActive(false);
            repository.save(oldKey);
            
            // 2. EVICT FROM CACHE specifically using the hash
            self.evictKeyFromCache(oldKey.getKeyHash());
            
            revokedCount++;
        }
        
        return revokedCount;
    }

    // Helper to trigger @CacheEvict
    //@CacheEvict(value = "apiKeys", key = "#hash")
    public void evictKeyFromCache(String hash) {
        log.info("Evicting API Key Hash from cache: {}", hash);
    }

    public String hashKey(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            log.error(e.getLocalizedMessage());
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
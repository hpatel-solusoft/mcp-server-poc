package com.solusoft.ai.mcp.security.service;

import org.springframework.stereotype.Service;

import com.solusoft.ai.mcp.features.claims.model.ApiKeyEntity;
import com.solusoft.ai.mcp.features.claims.repository.ApiKeyRepository;

import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@Service
@Slf4j
public class ApiKeyService {

    private final ApiKeyRepository repository;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiKeyService(ApiKeyRepository repository) {
        this.repository = repository;
    }

    public ApiKeyEntity validateKey(String plainTextKey) {
        String inputHash = hashKey(plainTextKey);
        return repository.findByHash(inputHash).orElse(null);
    }

    public String createApiKey(String owner, String role) {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String plainTextKey = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        ApiKeyEntity entity = new ApiKeyEntity();
        entity.setKeyHash(hashKey(plainTextKey));
        entity.setOwner(owner);
        entity.setRole(role);
        repository.save(entity);

        return plainTextKey;
    }

    public int revokeAllExceptLatest(String owner) {
        // 1. Get all active keys for this owner
        List<ApiKeyEntity> activeKeys = repository.findActiveByOwner(owner);
        
        if (activeKeys.size() <= 1) {
            return 0; // Nothing to prune (0 or 1 key exists)
        }

        // 2. Sort by ID Descending (Highest ID = Newest)
        // You could also use createdAt, but ID is safer/faster.
        activeKeys.sort((k1, k2) -> k2.getId().compareTo(k1.getId()));

        // 3. Identify the Winner (The first one in the list)
        ApiKeyEntity latestKey = activeKeys.get(0);
        
        // 4. Revoke the Losers (Everyone else)
        int revokedCount = 0;
        for (int i = 1; i < activeKeys.size(); i++) {
            ApiKeyEntity oldKey = activeKeys.get(i);
            oldKey.setActive(false);
            repository.save(oldKey);
            revokedCount++;
        }
        
        return revokedCount;
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
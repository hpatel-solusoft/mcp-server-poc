package com.solusoft.ai.mcp.security.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.solusoft.ai.mcp.security.model.GenerateKeyRequest;
import com.solusoft.ai.mcp.security.model.PruneRequest;
import com.solusoft.ai.mcp.security.service.ApiKeyService;

@RestController
@RequestMapping("/admin")
public class AdminKeyController {

    private final ApiKeyService apiKeyService;

    @Value("${mcp.security.admin-secret}")
    private String adminSecret;

    public AdminKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generateNewKey(
            @RequestHeader("X-ADMIN-SECRET") String secret,
            @RequestBody GenerateKeyRequest request) {

        if (!adminSecret.equals(secret)) return forbidden();

        try {
            String plainTextKey = apiKeyService.createApiKey(request.owner(), request.role());

            return ResponseEntity.ok(Map.of(
                "status", "created",
                "owner", request.owner(),
                "role", request.role(),
                "api_key", plainTextKey, 
                "message", "Key created successfully. Old keys are STILL ACTIVE."
            ));

        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/prune")
    public ResponseEntity<?> pruneOldKeys(
            @RequestHeader("X-ADMIN-SECRET") String secret,
            @RequestBody PruneRequest request) {

        if (!adminSecret.equals(secret)) return forbidden();

        try {
            // Logic is now handled entirely by the service
            int revokedCount = apiKeyService.revokeAllExceptLatest(request.owner());

            return ResponseEntity.ok(Map.of(
                "status", "pruned",
                "owner", request.owner(),
                "keys_revoked", revokedCount,
                "message", revokedCount > 0 
                    ? "Cleanup successful. kept the latest key, revoked " + revokedCount + " old ones."
                    : "No cleanup needed. Only 1 key was active."
            ));

        } catch (Exception e) {
            return error(e);
        }
    }

    // --- Helpers ---
    private ResponseEntity<?> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Invalid Admin Secret"));
    }
    private ResponseEntity<?> error(Exception e) {
        return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
    }
}
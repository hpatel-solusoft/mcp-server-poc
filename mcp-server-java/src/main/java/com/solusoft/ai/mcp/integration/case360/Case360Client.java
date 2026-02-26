package com.solusoft.ai.mcp.integration.case360;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.stereotype.Service;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.decorators.Decorators;

@Service
public class Case360Client {

    private final Case360SoapTemplate soapTemplate;
    private final Case360RecoveryHandler recoveryHandler;
    private final CircuitBreaker circuitBreaker;

    public Case360Client(Case360SoapTemplate soapTemplate, 
                         Case360RecoveryHandler recoveryHandler,
                         CircuitBreakerRegistry registry) {
        this.soapTemplate = soapTemplate;
        this.recoveryHandler = recoveryHandler;
        this.circuitBreaker = registry.circuitBreaker("case360Service");
    }

    public boolean ping() { return soapTemplate.executePing(); }

    public String getClaimStatus(String claimId) {
        return Decorators.ofSupplier(() -> soapTemplate.executeGetClaimStatus(claimId))
                .withCircuitBreaker(circuitBreaker)
                .withFallback((t) -> recoveryHandler.handleCriticalFailure(t, "getClaimStatus:" + claimId))
                .get();
    }

    public BigDecimal getCaseFolderTemplateId(String name) {
        return Decorators.ofSupplier(() -> soapTemplate.executeGetTemplateId(name, "getCaseTemplateIdFromName"))
                .withCircuitBreaker(circuitBreaker)
                .withFallback((t) -> recoveryHandler.handleCriticalFailure(t, "getCaseTemplateId:" + name))
                .get();
    }

    public BigDecimal getFilestoreTemplateId(String name) {
        return Decorators.ofSupplier(() -> soapTemplate.executeGetTemplateId(name, "getFileStoreTemplateId"))
                .withCircuitBreaker(circuitBreaker)
                .withFallback((t) -> recoveryHandler.handleCriticalFailure(t, "getFilestoreTemplateId:" + name))
                .get();
    }

    public String createCase(BigDecimal tid) {
        return Decorators.ofSupplier(() -> soapTemplate.executeCreateCase(tid))
                .withCircuitBreaker(circuitBreaker)
                .withFallback((t) -> recoveryHandler.handleCriticalFailure(t, "createCase"))
                .get();
    }

    public void updateCaseFields(String id, Map<String, Object> upd) {
        // FIXED: Using ofSupplier<Void> because DecorateRunnable lacks withFallback
        Decorators.ofSupplier(() -> {
            soapTemplate.executeUpdateFields(id, upd);
            return null;
        })
        .withCircuitBreaker(circuitBreaker)
        .withFallback((t) -> {
            recoveryHandler.handleVoidFailure(t, "updateCaseFields:" + id);
            return null;
        })
        .get();
    }

    public String createFileStore(BigDecimal tid) {
        return Decorators.ofSupplier(() -> soapTemplate.executeCreateFileStore(tid))
                .withCircuitBreaker(circuitBreaker)
                .withFallback((t) -> recoveryHandler.handleCriticalFailure(t, "createFileStore"))
                .get();
    }

    public void uploadDocument(BigDecimal id, byte[] bytes, String file) {
        // FIXED: Using ofSupplier<Void> to enable fallback handling
        Decorators.ofSupplier(() -> {
            soapTemplate.executeUploadDocument(id, bytes, file);
            return null;
        })
        .withCircuitBreaker(circuitBreaker)
        .withFallback((t) -> {
            recoveryHandler.handleVoidFailure(t, "uploadDocument:" + file);
            return null;
        })
        .get();
    }
    
    public void removeCaseFolder(String id) { soapTemplate.executeRemoveCaseFolder(id); }
    public void removeFileStore(String id) { soapTemplate.executeRemoveFileStore(id); }
}
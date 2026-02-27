package com.solusoft.ai.mcp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.resilience4j.core.registry.RegistryEventConsumer;
import io.github.resilience4j.retry.Retry;
import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class ResilienceLoggingConfig {

    @Bean
    public RegistryEventConsumer<Retry> retryEventConsumer() {
        return new RegistryEventConsumer<Retry>() {
            @Override
            public void onEntryAddedEvent(io.github.resilience4j.core.registry.EntryAddedEvent<Retry> entryAddedEvent) {
                entryAddedEvent.getAddedEntry().getEventPublisher()
                    .onRetry(event -> log.warn("[RETRY] Instance: {} | Attempt: {} | Error: {}", 
                        event.getName(), event.getNumberOfRetryAttempts(), event.getLastThrowable().getMessage()))
                    .onSuccess(event -> log.info("[RETRY SUCCESS] Instance: {} | Recovered on attempt: {}", 
                        event.getName(), event.getNumberOfRetryAttempts()))
                    .onError(event -> log.error("[RETRY FAILED] All attempts exhausted for Case360."));
            }

            @Override
            public void onEntryRemovedEvent(io.github.resilience4j.core.registry.EntryRemovedEvent<Retry> entryRemovedEvent) {}

            @Override
            public void onEntryReplacedEvent(io.github.resilience4j.core.registry.EntryReplacedEvent<Retry> entryReplacedEvent) {}
        };
    }
}
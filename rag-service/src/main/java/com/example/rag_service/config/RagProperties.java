package com.example.rag_service.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * All rag-service tuning in one validated place. A missing or invalid value stops
 * startup with a clear message instead of failing on the first request.
 */
@Validated
@ConfigurationProperties("rag")
public record RagProperties(
        @NotBlank String indexName,
        @Min(1) @Max(200) int topK,
        @Min(1) @Max(20) int contextChunks,
        @NotNull Duration searchTimeout,
        @NotNull Duration answerIdleTimeout,
        @NotBlank String storageDir,
        @Min(200) int chunkSize,
        @Min(0) int chunkOverlap,
        @Min(1) int redisMaxConnections
) {
}

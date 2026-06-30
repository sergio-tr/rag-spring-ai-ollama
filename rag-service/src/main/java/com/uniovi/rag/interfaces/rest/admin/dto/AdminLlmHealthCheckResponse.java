package com.uniovi.rag.interfaces.rest.admin.dto;

/**
 * Manual LLM health probe result (no secrets).
 */
public record AdminLlmHealthCheckResponse(
        String provider,
        String model,
        String baseUrl,
        String operation,
        boolean healthy,
        long latencyMs,
        String status,
        String message) {}

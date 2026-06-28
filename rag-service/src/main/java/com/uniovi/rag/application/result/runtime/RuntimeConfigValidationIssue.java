package com.uniovi.rag.application.result.runtime;

/**
 * Application-layer validation issue for runtime configuration (mapped to REST at the controller).
 */
public record RuntimeConfigValidationIssue(String code, String field, String message, String severity) {}

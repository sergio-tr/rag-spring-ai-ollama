package com.uniovi.rag.domain.knowledge;

/**
 * Versioned projection stored under METADATA {@code structuredSearchProjection} in JSONB (§8b).
 */
public record StructuredSearchProjection(int schemaVersion, String sourceFile, int charLength) {}

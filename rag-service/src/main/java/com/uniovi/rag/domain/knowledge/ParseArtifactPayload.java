package com.uniovi.rag.domain.knowledge;

/**
 * Canonical parse-stage payload shape; persisted JSON must include {@code schemaVersion}.
 */
public record ParseArtifactPayload(int schemaVersion, String normalizedText, String mimeType) {}

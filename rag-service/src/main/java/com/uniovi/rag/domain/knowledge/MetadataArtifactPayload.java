package com.uniovi.rag.domain.knowledge;

/**
 * Metadata-stage payload; STRUCTURED_SEARCH adds {@link StructuredSearchProjection} fields in JSON.
 */
public record MetadataArtifactPayload(
        int schemaVersion,
        String fileName,
        int textLength,
        StructuredSearchProjection structuredSearchProjection) {

    public MetadataArtifactPayload(int schemaVersion, String fileName, int textLength) {
        this(schemaVersion, fileName, textLength, null);
    }
}

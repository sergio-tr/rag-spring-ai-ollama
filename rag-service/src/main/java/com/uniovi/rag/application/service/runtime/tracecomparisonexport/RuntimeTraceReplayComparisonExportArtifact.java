package com.uniovi.rag.application.service.runtime.tracecomparisonexport;

import com.uniovi.rag.application.service.runtime.export.ZipExportArtifactSupport;

/**
 * Synchronous P21 replay-comparison ZIP export (single comparison per request).
 */
public record RuntimeTraceReplayComparisonExportArtifact(
        String filename,
        String mediaType,
        byte[] content,
        long sizeBytes) {

    public static final String MEDIA_TYPE_ZIP = "application/zip";

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RuntimeTraceReplayComparisonExportArtifact that)) return false;
        return ZipExportArtifactSupport.sameArtifact(
                filename, mediaType, content, sizeBytes, that.filename, that.mediaType, that.content, that.sizeBytes);
    }

    @Override
    public int hashCode() {
        return ZipExportArtifactSupport.artifactHash(filename, mediaType, content, sizeBytes);
    }

    @Override
    public String toString() {
        return ZipExportArtifactSupport.artifactToString(
                "RuntimeTraceReplayComparisonExportArtifact", filename, mediaType, content, sizeBytes);
    }
}

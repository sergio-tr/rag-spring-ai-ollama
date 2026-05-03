package com.uniovi.rag.application.service.runtime.tracereplayexport;

import com.uniovi.rag.application.service.runtime.export.ZipExportArtifactSupport;

/**
 * Synchronous P23 standalone replay ZIP export (one replay per request).
 */
public record RuntimeTraceReplayExportArtifact(
        String filename,
        String mediaType,
        byte[] content,
        long sizeBytes) {

    public static final String MEDIA_TYPE_ZIP = "application/zip";

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RuntimeTraceReplayExportArtifact that)) return false;
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
                "RuntimeTraceReplayExportArtifact", filename, mediaType, content, sizeBytes);
    }
}

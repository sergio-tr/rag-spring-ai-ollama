package com.uniovi.rag.application.service.runtime.tracecomparisonbatchexport;

import com.uniovi.rag.application.service.runtime.export.ZipExportArtifactSupport;

/**
 * P26 batch export HTTP payload: final ZIP bytes + suggested filename + length.
 */
public record RuntimeTraceReplayComparisonBatchExportArtifact(
        String filename, String mediaType, byte[] content, long sizeBytes) {

    public static final String MEDIA_TYPE_ZIP = "application/zip";

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RuntimeTraceReplayComparisonBatchExportArtifact that)) return false;
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
                "RuntimeTraceReplayComparisonBatchExportArtifact", filename, mediaType, content, sizeBytes);
    }
}

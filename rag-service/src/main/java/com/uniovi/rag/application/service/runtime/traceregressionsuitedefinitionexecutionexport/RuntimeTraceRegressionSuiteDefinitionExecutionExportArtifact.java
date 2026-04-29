package com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionexecutionexport;

import com.uniovi.rag.application.service.runtime.export.ZipExportArtifactSupport;

/**
 * P37: final ZIP bytes + filename + declared length for HTTP (must match {@code content.length}).
 */
public record RuntimeTraceRegressionSuiteDefinitionExecutionExportArtifact(
        String filename, String mediaType, byte[] content, long sizeBytes) {

    public static final String MEDIA_TYPE_ZIP = "application/zip";

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RuntimeTraceRegressionSuiteDefinitionExecutionExportArtifact that)) return false;
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
                "RuntimeTraceRegressionSuiteDefinitionExecutionExportArtifact", filename, mediaType, content, sizeBytes);
    }
}

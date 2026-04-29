package com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionexport;

import com.uniovi.rag.application.service.runtime.export.ZipExportArtifactSupport;

/**
 * P38 definition export ZIP HTTP artifact metadata and body bytes.
 */
public record RuntimeTraceRegressionSuiteDefinitionExportArtifact(
        String filename, String mediaType, byte[] content, long sizeBytes) {

    public static final String MEDIA_TYPE_ZIP = "application/zip";

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RuntimeTraceRegressionSuiteDefinitionExportArtifact that)) return false;
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
                "RuntimeTraceRegressionSuiteDefinitionExportArtifact", filename, mediaType, content, sizeBytes);
    }
}

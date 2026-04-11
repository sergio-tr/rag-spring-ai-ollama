package com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionexecutionexport;

/**
 * P37: final ZIP bytes + filename + declared length for HTTP (must match {@code content.length}).
 */
public record RuntimeTraceRegressionSuiteDefinitionExecutionExportArtifact(
        String filename, String mediaType, byte[] content, long sizeBytes) {

    public static final String MEDIA_TYPE_ZIP = "application/zip";
}

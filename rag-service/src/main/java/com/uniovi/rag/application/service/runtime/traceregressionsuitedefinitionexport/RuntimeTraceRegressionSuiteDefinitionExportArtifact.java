package com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionexport;

/**
 * P38 definition export ZIP HTTP artifact metadata and body bytes.
 */
public record RuntimeTraceRegressionSuiteDefinitionExportArtifact(
        String filename, String mediaType, byte[] content, long sizeBytes) {

    public static final String MEDIA_TYPE_ZIP = "application/zip";
}

package com.uniovi.rag.application.service.runtime.traceregressionsuiteexport;

/**
 * P32 export HTTP payload: final ZIP bytes + suggested filename + length.
 */
public record RuntimeTraceRegressionSuiteExportArtifact(String filename, String mediaType, byte[] content, long sizeBytes) {

    public static final String MEDIA_TYPE_ZIP = "application/zip";
}

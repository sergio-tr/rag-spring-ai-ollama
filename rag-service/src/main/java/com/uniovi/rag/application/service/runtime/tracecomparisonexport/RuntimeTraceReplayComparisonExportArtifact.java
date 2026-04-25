package com.uniovi.rag.application.service.runtime.tracecomparisonexport;

/**
 * Synchronous P21 replay-comparison ZIP export (single comparison per request).
 */
public record RuntimeTraceReplayComparisonExportArtifact(
        String filename,
        String mediaType,
        byte[] content,
        long sizeBytes) {

    public static final String MEDIA_TYPE_ZIP = "application/zip";
}

package com.uniovi.rag.application.service.runtime.tracereplayexport;

/**
 * Synchronous P23 standalone replay ZIP export (one replay per request).
 */
public record RuntimeTraceReplayExportArtifact(
        String filename,
        String mediaType,
        byte[] content,
        long sizeBytes) {

    public static final String MEDIA_TYPE_ZIP = "application/zip";
}

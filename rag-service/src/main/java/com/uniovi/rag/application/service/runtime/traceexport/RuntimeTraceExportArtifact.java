package com.uniovi.rag.application.service.runtime.traceexport;

/**
 * Immutable application-layer export artifact returned by {@link RuntimeTraceExportService}.
 * Controllers adapt this to HTTP responses.
 */
public record RuntimeTraceExportArtifact(
        String filename,
        String mediaType,
        byte[] content,
        long sizeBytes,
        String exportKind) {}


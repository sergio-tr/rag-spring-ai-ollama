package com.uniovi.rag.application.service.runtime.tracecomparisonbatchexport;

/**
 * P26 batch export HTTP payload: final ZIP bytes + suggested filename + length.
 */
public record RuntimeTraceReplayComparisonBatchExportArtifact(
        String filename, String mediaType, byte[] content, long sizeBytes) {

    public static final String MEDIA_TYPE_ZIP = "application/zip";
}

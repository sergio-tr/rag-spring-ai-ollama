package com.uniovi.rag.application.service.runtime.tracereplaybatchexport;

/**
 * P29 batch export HTTP payload: final ZIP bytes + suggested filename + length.
 */
public record RuntimeTraceReplayBatchExportArtifact(String filename, String mediaType, byte[] content, long sizeBytes) {

    public static final String MEDIA_TYPE_ZIP = "application/zip";
}

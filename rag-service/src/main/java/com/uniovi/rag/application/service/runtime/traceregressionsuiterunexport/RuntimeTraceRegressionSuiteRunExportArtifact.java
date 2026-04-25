package com.uniovi.rag.application.service.runtime.traceregressionsuiterunexport;

/** P43 run export ZIP HTTP artifact metadata and body bytes. */
public record RuntimeTraceRegressionSuiteRunExportArtifact(String filename, String mediaType, byte[] content, long sizeBytes) {

    public static final String MEDIA_TYPE_ZIP = "application/zip";
}

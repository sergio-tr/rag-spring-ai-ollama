package com.uniovi.rag.application.service.runtime.traceexport;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable application-layer export artifact returned by {@link RuntimeTraceExportService}.
 * Controllers adapt this to HTTP responses.
 */
public record RuntimeTraceExportArtifact(
        String filename,
        String mediaType,
        byte[] content,
        long sizeBytes,
        String exportKind) {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RuntimeTraceExportArtifact that)) return false;
        return sizeBytes == that.sizeBytes
                && Objects.equals(filename, that.filename)
                && Objects.equals(mediaType, that.mediaType)
                && Arrays.equals(content, that.content)
                && Objects.equals(exportKind, that.exportKind);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(filename, mediaType, sizeBytes, exportKind);
        result = 31 * result + Arrays.hashCode(content);
        return result;
    }

    @Override
    public String toString() {
        return "RuntimeTraceExportArtifact["
                + "filename=" + filename
                + ", mediaType=" + mediaType
                + ", content(len=" + (content == null ? 0 : content.length) + ", hash=" + Arrays.hashCode(content) + ")"
                + ", sizeBytes=" + sizeBytes
                + ", exportKind=" + exportKind
                + "]";
    }
}


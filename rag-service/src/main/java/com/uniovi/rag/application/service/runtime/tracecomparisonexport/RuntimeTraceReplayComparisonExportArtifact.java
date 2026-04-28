package com.uniovi.rag.application.service.runtime.tracecomparisonexport;

import java.util.Arrays;
import java.util.Objects;

/**
 * Synchronous P21 replay-comparison ZIP export (single comparison per request).
 */
public record RuntimeTraceReplayComparisonExportArtifact(
        String filename,
        String mediaType,
        byte[] content,
        long sizeBytes) {

    public static final String MEDIA_TYPE_ZIP = "application/zip";

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RuntimeTraceReplayComparisonExportArtifact that)) return false;
        return sizeBytes == that.sizeBytes
                && Objects.equals(filename, that.filename)
                && Objects.equals(mediaType, that.mediaType)
                && Arrays.equals(content, that.content);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(filename, mediaType, sizeBytes);
        result = 31 * result + Arrays.hashCode(content);
        return result;
    }

    @Override
    public String toString() {
        return "RuntimeTraceReplayComparisonExportArtifact["
                + "filename=" + filename
                + ", mediaType=" + mediaType
                + ", content(len=" + (content == null ? 0 : content.length) + ", hash=" + Arrays.hashCode(content) + ")"
                + ", sizeBytes=" + sizeBytes
                + "]";
    }
}

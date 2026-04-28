package com.uniovi.rag.application.service.runtime.traceregressionsuiterunexport;

/** P43 run export ZIP HTTP artifact metadata and body bytes. */
public record RuntimeTraceRegressionSuiteRunExportArtifact(String filename, String mediaType, byte[] content, long sizeBytes) {

    public static final String MEDIA_TYPE_ZIP = "application/zip";

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RuntimeTraceRegressionSuiteRunExportArtifact that)) return false;
        return sizeBytes == that.sizeBytes
                && java.util.Objects.equals(filename, that.filename)
                && java.util.Objects.equals(mediaType, that.mediaType)
                && java.util.Arrays.equals(content, that.content);
    }

    @Override
    public int hashCode() {
        int result = java.util.Objects.hash(filename, mediaType, sizeBytes);
        result = 31 * result + java.util.Arrays.hashCode(content);
        return result;
    }

    @Override
    public String toString() {
        return "RuntimeTraceRegressionSuiteRunExportArtifact["
                + "filename=" + filename
                + ", mediaType=" + mediaType
                + ", content(len=" + (content == null ? 0 : content.length) + ", hash=" + java.util.Arrays.hashCode(content) + ")"
                + ", sizeBytes=" + sizeBytes
                + "]";
    }
}

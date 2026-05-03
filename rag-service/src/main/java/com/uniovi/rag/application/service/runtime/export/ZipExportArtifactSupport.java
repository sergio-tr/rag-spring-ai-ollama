package com.uniovi.rag.application.service.runtime.export;

import java.util.Arrays;
import java.util.Objects;

public final class ZipExportArtifactSupport {

    private ZipExportArtifactSupport() {
    }

    public static boolean sameArtifact(
            String filename,
            String mediaType,
            byte[] content,
            long sizeBytes,
            String otherFilename,
            String otherMediaType,
            byte[] otherContent,
            long otherSizeBytes) {
        return sizeBytes == otherSizeBytes
                && Objects.equals(filename, otherFilename)
                && Objects.equals(mediaType, otherMediaType)
                && Arrays.equals(content, otherContent);
    }

    public static int artifactHash(String filename, String mediaType, byte[] content, long sizeBytes) {
        int result = Objects.hash(filename, mediaType, sizeBytes);
        result = 31 * result + Arrays.hashCode(content);
        return result;
    }

    public static String artifactToString(
            String typeName,
            String filename,
            String mediaType,
            byte[] content,
            long sizeBytes) {
        return typeName + "["
                + "filename=" + filename
                + ", mediaType=" + mediaType
                + ", content(len=" + (content == null ? 0 : content.length) + ", hash=" + Arrays.hashCode(content) + ")"
                + ", sizeBytes=" + sizeBytes
                + "]";
    }
}

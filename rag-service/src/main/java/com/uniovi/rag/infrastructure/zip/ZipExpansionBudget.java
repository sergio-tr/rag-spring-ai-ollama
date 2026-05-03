package com.uniovi.rag.infrastructure.zip;

import java.io.IOException;

/**
 * Tracks cumulative uncompressed bytes and entry count while reading a ZIP from an untrusted source.
 *
 * <p>Callers should create one budget per {@link java.util.zip.ZipInputStream} parse and share it across
 * {@link ZipIoGuards#readStoredEntryBytes} invocations so total expansion stays bounded (zip-bomb mitigation).
 */
public final class ZipExpansionBudget {

    private final long maxTotalUncompressedBytes;
    private final int maxEntries;
    private long totalUncompressedBytes;
    private int entriesStarted;

    /**
     * Budget suitable for strict two-file regression imports: total uncompressed payload cannot exceed the uploaded
     * archive size cap, and the number of processed entries is capped.
     */
    public static ZipExpansionBudget forUploadedZip(long maxZipFileBytes) {
        return new ZipExpansionBudget(maxZipFileBytes, 32);
    }

    private ZipExpansionBudget(long maxTotalUncompressedBytes, int maxEntries) {
        if (maxTotalUncompressedBytes < 0) {
            throw new IllegalArgumentException("maxTotalUncompressedBytes must be non-negative");
        }
        if (maxEntries < 1) {
            throw new IllegalArgumentException("maxEntries must be positive");
        }
        this.maxTotalUncompressedBytes = maxTotalUncompressedBytes;
        this.maxEntries = maxEntries;
    }

    void beginEntry() throws IOException {
        entriesStarted++;
        if (entriesStarted > maxEntries) {
            throw new IOException("zip has too many entries");
        }
    }

    void recordUncompressedBytes(long n) throws IOException {
        if (n < 0) {
            throw new IOException("invalid read length");
        }
        if (n == 0) {
            return;
        }
        totalUncompressedBytes = Math.addExact(totalUncompressedBytes, n);
        if (totalUncompressedBytes > maxTotalUncompressedBytes) {
            throw new IOException("zip uncompressed data exceeds limit");
        }
    }
}

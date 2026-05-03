package com.uniovi.rag.infrastructure.zip;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Guard rails for reading ZIP payloads safely (zip-slip / zip-bomb protection).
 *
 * <p>This repository accepts only tiny, strict ZIPs with fixed entry names; this helper adds structural validation and
 * enforces size invariants while reading entry bytes from the stream (not from forged headers alone).
 */
public final class ZipIoGuards {

    private static final int READ_BUFFER_SIZE = 8192;

    private ZipIoGuards() {}

    public static void requireSafeEntryName(String name) throws IOException {
        if (name == null || name.isBlank()) {
            throw new IOException("invalid zip entry name");
        }
        // Prevent zip-slip style traversal even if callers later decide to write entries to disk.
        if (name.contains("..") || name.startsWith("/") || name.startsWith("\\") || name.contains(":")) {
            throw new IOException("unsafe zip entry name");
        }
    }

    /**
     * Reads a STORED entry by measuring actual bytes from the stream (S5042); use {@link ZipExpansionBudget} to cap
     * cumulative expansion across the whole archive.
     */
    public static byte[] readStoredEntryBytes(
            ZipInputStream zin, ZipEntry entry, long maxBytesPerEntry, ZipExpansionBudget budget) throws IOException {
        validateStoredEntryBasics(entry, maxBytesPerEntry);
        budget.beginEntry();

        long declaredSize = entry.getSize();
        long declaredCompressed = entry.getCompressedSize();
        int initialCapacity =
                declaredSize >= 0 && declaredSize <= maxBytesPerEntry
                        ? (int) Math.min(declaredSize, READ_BUFFER_SIZE)
                        : READ_BUFFER_SIZE;
        ByteArrayOutputStream out = new ByteArrayOutputStream(initialCapacity);
        byte[] buf = new byte[READ_BUFFER_SIZE];
        byte[] result = readStoredPayload(zin, budget, maxBytesPerEntry, buf, out);

        assertStoredSizesMatchDeclared(declaredSize, declaredCompressed, result.length);
        return result;
    }

    private static void validateStoredEntryBasics(ZipEntry entry, long maxBytesPerEntry) throws IOException {
        if (entry == null) {
            throw new IOException("missing zip entry");
        }
        requireSafeEntryName(entry.getName());
        if (entry.isDirectory()) {
            throw new IOException("zip entry is a directory");
        }
        if (entry.getMethod() != ZipEntry.STORED) {
            throw new IOException("zip entry must be STORED");
        }
        if (entry.getCrc() < 0) {
            throw new IOException("zip entry CRC missing");
        }
        rejectOversizedDeclaredHeaders(entry, maxBytesPerEntry);
    }

    private static void rejectOversizedDeclaredHeaders(ZipEntry entry, long maxBytesPerEntry) throws IOException {
        long declaredSize = entry.getSize();
        long declaredCompressed = entry.getCompressedSize();
        if (declaredSize >= 0 && declaredSize > maxBytesPerEntry) {
            throw new IOException("zip entry too large");
        }
        if (declaredSize > Integer.MAX_VALUE) {
            throw new IOException("zip entry too large for memory");
        }
        if (declaredCompressed >= 0 && declaredCompressed > maxBytesPerEntry) {
            throw new IOException("zip entry too large");
        }
    }

    private static byte[] readStoredPayload(
            ZipInputStream zin, ZipExpansionBudget budget, long maxBytesPerEntry, byte[] buf, ByteArrayOutputStream out)
            throws IOException {
        long totalForEntry = 0L;
        int n;
        while ((n = zin.read(buf)) > 0) {
            long nextTotal = Math.addExact(totalForEntry, n);
            if (nextTotal > maxBytesPerEntry) {
                throw new IOException("zip entry too large");
            }
            budget.recordUncompressedBytes(n);
            out.write(buf, 0, n);
            totalForEntry = nextTotal;
        }
        return out.toByteArray();
    }

    private static void assertStoredSizesMatchDeclared(long declaredSize, long declaredCompressed, int actualLength)
            throws IOException {
        if (declaredSize >= 0 && actualLength != declaredSize) {
            throw new IOException("zip entry size mismatch");
        }
        if (declaredCompressed >= 0 && actualLength != declaredCompressed) {
            throw new IOException("zip entry size mismatch");
        }
    }
}

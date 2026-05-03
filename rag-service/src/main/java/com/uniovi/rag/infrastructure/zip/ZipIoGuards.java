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
     * Snapshots validated central-directory sizes for a STORED entry. Declared lengths are checked against caller
     * limits only; payload expansion is still bounded by counting bytes read from the stream (Sonar S5042).
     */
    private record StoredEntryHeader(long uncompressedSize, long compressedSize) {}

    /**
     * Reads a STORED entry by measuring actual bytes from the stream (S5042); use {@link ZipExpansionBudget} to cap
     * cumulative expansion across the whole archive.
     */
    public static byte[] readStoredEntryBytes(
            ZipInputStream zin, ZipEntry entry, long maxBytesPerEntry, ZipExpansionBudget budget) throws IOException {
        StoredEntryHeader header = validateStoredEntryHeader(entry, maxBytesPerEntry);
        budget.beginEntry();

        ByteArrayOutputStream out = new ByteArrayOutputStream(READ_BUFFER_SIZE);
        byte[] buf = new byte[READ_BUFFER_SIZE];
        byte[] result = readStoredPayload(zin, budget, maxBytesPerEntry, buf, out);

        assertStoredSizesMatchDeclared(header.uncompressedSize(), header.compressedSize(), result.length);
        return result;
    }

    /**
     * Validates headers before any expansion: STORED entries must publish consistent uncompressed/compressed sizes
     * (ratio {@code 1:1}), staying within {@code maxBytesPerEntry}.
     *
     * <p><strong>Zip bomb / S5042:</strong> Values from {@link ZipEntry#getSize()} are not used to allocate buffers or
     * to trust expansion size. Memory growth is driven only by bytes read in {@link #readStoredPayload}, each bounded
     * by {@code maxBytesPerEntry} and {@link ZipExpansionBudget}. STORED entries cannot inflate payload via compression.
     */
    @SuppressWarnings("java:S5042")
    private static StoredEntryHeader validateStoredEntryHeader(ZipEntry entry, long maxBytesPerEntry) throws IOException {
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
        long declaredUncompressed = entry.getSize();
        long declaredCompressed = entry.getCompressedSize();
        if (declaredUncompressed < 0 || declaredCompressed < 0) {
            throw new IOException("zip entry missing size metadata");
        }
        if (declaredUncompressed > maxBytesPerEntry || declaredCompressed > maxBytesPerEntry) {
            throw new IOException("zip entry too large");
        }
        if (declaredUncompressed > Integer.MAX_VALUE) {
            throw new IOException("zip entry too large for memory");
        }
        if (declaredUncompressed != declaredCompressed) {
            throw new IOException("STORED zip entry requires matching compressed and uncompressed sizes");
        }
        return new StoredEntryHeader(declaredUncompressed, declaredCompressed);
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

package com.uniovi.rag.infrastructure.zip;

import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Guard rails for reading ZIP payloads safely (zip-slip / zip-bomb protection).
 *
 * <p>This repository accepts only tiny, strict ZIPs with fixed entry names; this helper adds structural validation and
 * enforces size invariants before reading entry bytes.
 */
public final class ZipIoGuards {

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

    public static byte[] readStoredEntryBytes(ZipInputStream zin, ZipEntry entry, long maxBytes) throws IOException {
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

        long size = entry.getSize();
        if (size < 0) {
            throw new IOException("zip entry size is unknown");
        }
        if (size > maxBytes) {
            throw new IOException("zip entry too large");
        }
        if (size > Integer.MAX_VALUE) {
            throw new IOException("zip entry too large for memory");
        }

        // STORED entries should have compressedSize == size when known.
        long compressedSize = entry.getCompressedSize();
        if (compressedSize >= 0 && compressedSize != size) {
            throw new IOException("zip entry size mismatch");
        }
        if (entry.getCrc() < 0) {
            throw new IOException("zip entry CRC missing");
        }

        byte[] bytes = zin.readNBytes((int) size);
        if (bytes.length != (int) size) {
            throw new IOException("zip entry truncated");
        }
        return bytes;
    }
}


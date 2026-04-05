package com.uniovi.rag.infrastructure.storage;

import com.uniovi.rag.application.port.EvaluationDatasetStorePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Filesystem-backed {@link EvaluationDatasetStorePort} (same pattern as {@link LocalBinaryStorageAdapter}).
 */
@Component
public class LocalEvaluationDatasetStorageAdapter implements EvaluationDatasetStorePort {

    private final Path root;
    private final long maxUploadBytes;

    public LocalEvaluationDatasetStorageAdapter(
            @Value("${rag.evaluation.storage-root:}") String rootPath,
            @Value("${rag.evaluation.max-upload-bytes:26214400}") long maxUploadBytes) {
        String r = (rootPath != null && !rootPath.isBlank())
                ? rootPath
                : System.getProperty("java.io.tmpdir") + java.io.File.separator + "rag-evaluation-datasets";
        this.root = Path.of(r).toAbsolutePath().normalize();
        this.maxUploadBytes = maxUploadBytes > 0 ? maxUploadBytes : 25L * 1024 * 1024;
    }

    @Override
    public StoredDataset store(InputStream data, long byteSize, String relativeKeyHint) throws IOException {
        if (byteSize > maxUploadBytes) {
            throw new IOException("Dataset file exceeds limit (" + maxUploadBytes + " bytes)");
        }
        String safe = sanitizeRelativeKey(relativeKeyHint);
        Path target = root.resolve(safe).normalize();
        if (!target.startsWith(root)) {
            throw new IOException("path escapes evaluation storage root");
        }
        Files.createDirectories(target.getParent());
        MessageDigest md = digest();
        try (DigestInputStream in = new DigestInputStream(new BufferedInputStream(data), md);
                var out = Files.newOutputStream(target)) {
            in.transferTo(out);
        }
        String hash = HexFormat.of().formatHex(md.digest());
        return new StoredDataset(toRelativeUriString(target), hash, Files.size(target));
    }

    @Override
    public InputStream openStream(String storageUri) throws IOException {
        return new BufferedInputStream(Files.newInputStream(resolveExisting(storageUri)));
    }

    @Override
    public void delete(String storageUri) throws IOException {
        Files.deleteIfExists(resolveExisting(storageUri));
    }

    private Path resolveExisting(String relativeUri) throws IOException {
        Path p = root.resolve(relativeUri).normalize();
        if (!p.startsWith(root)) {
            throw new IOException("path escapes evaluation storage root");
        }
        if (!Files.exists(p)) {
            throw new IOException("object not found: " + relativeUri);
        }
        return p;
    }

    private String toRelativeUriString(Path absolute) {
        return root.relativize(absolute).toString().replace('\\', '/');
    }

    private static String sanitizeRelativeKey(String hint) {
        String h = hint != null ? hint : "dataset.bin";
        return h.replace("..", "_").replace('\\', '/');
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

}

package com.uniovi.rag.infrastructure.storage;

import com.uniovi.rag.application.port.BinaryStoragePort;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Filesystem-backed {@link BinaryStoragePort} under {@code rag.storage.root}.
 */
@Component
public class LocalBinaryStorageAdapter implements BinaryStoragePort {

    private final Path root;

    public LocalBinaryStorageAdapter(@Value("${rag.storage.root:}") String rootPath) {
        String r = (rootPath != null && !rootPath.isBlank())
                ? rootPath
                : System.getProperty("java.io.tmpdir") + File.separator + "rag-storage";
        this.root = Path.of(r).toAbsolutePath().normalize();
    }

    @Override
    public StoredObject store(InputStream data, long byteSize, String relativeKeyHint) throws IOException {
        String safe = sanitizeRelativeKey(relativeKeyHint);
        Path target = root.resolve(safe).normalize();
        if (!target.startsWith(root)) {
            throw new IOException("path escapes storage root");
        }
        Files.createDirectories(target.getParent());
        MessageDigest md = digest();
        try (DigestInputStream in = new DigestInputStream(new BufferedInputStream(data), md);
                var out = Files.newOutputStream(target)) {
            in.transferTo(out);
        }
        String hash = HexFormat.of().formatHex(md.digest());
        return new StoredObject(toRelativeUriString(target), hash);
    }

    @Override
    public InputStream openStream(String relativeUri) throws IOException {
        return new BufferedInputStream(Files.newInputStream(resolveExisting(relativeUri)));
    }

    @Override
    public boolean isReadableNonEmpty(String relativeUri) {
        if (relativeUri == null || relativeUri.isBlank()) {
            return false;
        }
        try {
            Path p = root.resolve(relativeUri.startsWith("/") ? relativeUri.substring(1) : relativeUri).normalize();
            if (!p.startsWith(root)) {
                return false;
            }
            return Files.isRegularFile(p) && Files.size(p) > 0L;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public void delete(String relativeUri) throws IOException {
        Files.deleteIfExists(resolveExisting(relativeUri));
    }

    @Override
    public StoredObject linkOrCopy(String sourceRelativeUri, String targetRelativeKeyHint) throws IOException {
        Path src = resolveExisting(sourceRelativeUri);
        String safe = sanitizeRelativeKey(targetRelativeKeyHint);
        Path dst = root.resolve(safe).normalize();
        if (!dst.startsWith(root)) {
            throw new IOException("path escapes storage root");
        }
        Files.createDirectories(dst.getParent());
        try {
            Files.createLink(dst, src);
        } catch (UnsupportedOperationException | IOException e) {
            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
        }
        byte[] fileBytes = Files.readAllBytes(dst);
        MessageDigest md = digest();
        String hash = HexFormat.of().formatHex(md.digest(fileBytes));
        return new StoredObject(toRelativeUriString(dst), hash);
    }

    private Path resolveExisting(String relativeUri) throws IOException {
        Path p = root.resolve(relativeUri.startsWith("/") ? relativeUri.substring(1) : relativeUri).normalize();
        if (!p.startsWith(root)) {
            throw new IOException("path escapes storage root");
        }
        if (!Files.isRegularFile(p)) {
            throw new IOException("not found: " + relativeUri);
        }
        return p;
    }

    private String toRelativeUriString(Path absolute) {
        Path rel = root.relativize(absolute.normalize());
        return rel.toString().replace('\\', '/');
    }

    private static String sanitizeRelativeKey(String hint) {
        if (hint == null || hint.isBlank()) {
            return "bin/" + UUID.randomUUID();
        }
        String t = hint.replace('\\', '/').trim();
        while (t.startsWith("/")) {
            t = t.substring(1);
        }
        if (t.contains("..")) {
            throw new IllegalArgumentException("invalid relative key");
        }
        return t;
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

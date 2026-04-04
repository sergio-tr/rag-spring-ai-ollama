package com.uniovi.rag.application.port;

import java.io.IOException;
import java.io.InputStream;

/**
 * Persists uploaded binaries under a configurable root (filesystem adapter).
 */
public interface BinaryStoragePort {

    record StoredObject(String relativeUri, String sha256Hex) {
    }

    StoredObject store(InputStream data, long byteSize, String relativeKeyHint) throws IOException;

    InputStream openStream(String relativeUri) throws IOException;

    void delete(String relativeUri) throws IOException;

    /**
     * Hardlink when same volume; otherwise byte copy. Returns the new stored object descriptor.
     */
    StoredObject linkOrCopy(String sourceRelativeUri, String targetRelativeKeyHint) throws IOException;
}

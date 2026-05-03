package com.uniovi.rag.application.port;

import java.io.IOException;
import java.io.InputStream;

/**
 * Persists evaluation dataset binaries under {@code rag.evaluation.storage-root}.
 */
public interface EvaluationDatasetStorePort {

    record StoredDataset(String storageUri, String sha256Hex, long byteSize) {}

    /**
     * Stores bytes under a namespaced key; computes SHA-256 while streaming.
     */
    StoredDataset store(InputStream data, long byteSize, String relativeKeyHint) throws IOException;

    InputStream openStream(String storageUri) throws IOException;

    void delete(String storageUri) throws IOException;
}

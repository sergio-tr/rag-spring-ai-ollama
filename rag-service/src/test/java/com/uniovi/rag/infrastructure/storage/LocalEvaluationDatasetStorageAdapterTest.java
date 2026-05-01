package com.uniovi.rag.infrastructure.storage;

import com.uniovi.rag.application.port.EvaluationDatasetStorePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalEvaluationDatasetStorageAdapterTest {

    @Test
    void store_openStream_delete_roundTrip(@TempDir Path root) throws Exception {
        LocalEvaluationDatasetStorageAdapter adapter =
                new LocalEvaluationDatasetStorageAdapter(root.toString(), 1024 * 1024);

        byte[] data = "eval-data".getBytes(StandardCharsets.UTF_8);
        EvaluationDatasetStorePort.StoredDataset stored =
                adapter.store(new ByteArrayInputStream(data), data.length, "runs/a.bin");

        assertEquals("runs/a.bin", stored.storageUri().replace('\\', '/'));

        try (var in = adapter.openStream(stored.storageUri())) {
            assertArrayEquals(data, in.readAllBytes());
        }

        adapter.delete(stored.storageUri());
        assertThrows(IOException.class, () -> adapter.openStream(stored.storageUri()));
    }

    @Test
    void store_rejectsOversizedHint() {
        LocalEvaluationDatasetStorageAdapter adapter =
                new LocalEvaluationDatasetStorageAdapter("", 10);
        assertThrows(
                IOException.class,
                () ->
                        adapter.store(
                                new ByteArrayInputStream(new byte[20]), 20, "x.bin"));
    }

    @Test
    void sanitizeRelativeKey_defaultsWhenNull() throws Exception {
        LocalEvaluationDatasetStorageAdapter adapter =
                new LocalEvaluationDatasetStorageAdapter("", 1024 * 1024);
        byte[] one = new byte[] {9};
        EvaluationDatasetStorePort.StoredDataset stored =
                adapter.store(new ByteArrayInputStream(one), 1, null);
        assertEquals(true, stored.storageUri().contains("dataset.bin"));
    }
}

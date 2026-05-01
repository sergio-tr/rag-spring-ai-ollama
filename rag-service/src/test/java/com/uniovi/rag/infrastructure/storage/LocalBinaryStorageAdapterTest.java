package com.uniovi.rag.infrastructure.storage;

import com.uniovi.rag.application.port.BinaryStoragePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalBinaryStorageAdapterTest {

    @Test
    void store_openStream_delete_roundTrip(@TempDir Path root) throws Exception {
        LocalBinaryStorageAdapter adapter = new LocalBinaryStorageAdapter(root.toString());
        byte[] data = "payload".getBytes(StandardCharsets.UTF_8);

        BinaryStoragePort.StoredObject stored =
                adapter.store(new ByteArrayInputStream(data), data.length, "test/key.bin");

        assertEquals("test/key.bin", stored.relativeUri().replace('\\', '/'));

        try (var in = adapter.openStream(stored.relativeUri())) {
            assertArrayEquals(data, in.readAllBytes());
        }

        adapter.delete(stored.relativeUri());
        assertThrows(IOException.class, () -> adapter.openStream(stored.relativeUri()));
    }

    @Test
    void linkOrCopy_copiesWhenHardlinkUnsupported(@TempDir Path root) throws Exception {
        LocalBinaryStorageAdapter adapter = new LocalBinaryStorageAdapter(root.toString());
        byte[] data = new byte[] {1, 2, 3};
        BinaryStoragePort.StoredObject first =
                adapter.store(new ByteArrayInputStream(data), data.length, "a/x.bin");

        BinaryStoragePort.StoredObject second = adapter.linkOrCopy(first.relativeUri(), "b/y.bin");

        assertEquals("b/y.bin", second.relativeUri().replace('\\', '/'));
        try (var in = adapter.openStream(second.relativeUri())) {
            assertArrayEquals(data, in.readAllBytes());
        }
    }

    @Test
    void store_rejectsPathTraversal(@TempDir Path root) {
        LocalBinaryStorageAdapter adapter = new LocalBinaryStorageAdapter(root.toString());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        adapter.store(
                                new ByteArrayInputStream(new byte[] {1}),
                                1,
                                "../../outside"));
    }

}

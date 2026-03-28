package com.uniovi.rag.model;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class SimpleMultipartFileTest {

    @Test
    void gettersAndIsEmpty() throws IOException {
        byte[] data = "hello".getBytes();
        SimpleMultipartFile f = new SimpleMultipartFile("param", "file.txt", "text/plain", data);
        assertEquals("param", f.getName());
        assertEquals("file.txt", f.getOriginalFilename());
        assertEquals("text/plain", f.getContentType());
        assertFalse(f.isEmpty());
        assertEquals(5, f.getSize());
        assertArrayEquals(data, f.getBytes());
    }

    @Test
    void nullName_defaultsToFile() {
        SimpleMultipartFile f = new SimpleMultipartFile(null, "a", "b", new byte[1]);
        assertEquals("file", f.getName());
    }

    @Test
    void nullContent_defaultsToEmptyArray() throws IOException {
        SimpleMultipartFile f = new SimpleMultipartFile("x", "a", "b", null);
        assertTrue(f.isEmpty());
        assertEquals(0, f.getSize());
        assertNotNull(f.getBytes());
        assertEquals(0, f.getBytes().length);
    }

    @Test
    void getInputStream_returnsContent() throws IOException {
        byte[] data = "content".getBytes();
        SimpleMultipartFile f = new SimpleMultipartFile("n", "o", "c", data);
        try (var is = f.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4];
            int n;
            while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
            assertArrayEquals(data, out.toByteArray());
        }
    }

    @Test
    void transferTo_writesToFile() throws IOException {
        byte[] data = "written".getBytes();
        SimpleMultipartFile f = new SimpleMultipartFile("n", "o", "c", data);
        File dest = Files.createTempFile("smp", ".tmp").toFile();
        try {
            f.transferTo(dest);
            assertArrayEquals(data, Files.readAllBytes(dest.toPath()));
        } finally {
            dest.delete();
        }
    }
}

package com.uniovi.rag.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagApiPathAndAccountAndRuntimePropertiesTest {

    @Test
    void ragApiPathPropertiesNormalizesPrefixes() {
        RagApiPathProperties p = new RagApiPathProperties();
        p.setProductBasePath("  ");
        assertEquals("/api/v5", p.getProductBasePath());
        p.setProductBasePath("v5/x");
        assertEquals("/v5/x", p.getProductBasePath());
        p.setProductBasePath("/api/v5/");
        assertEquals("/api/v5", p.getProductBasePath());
    }

    @Test
    void ragAccountPropertiesBindExportDirAndTtl(@TempDir Path dir) {
        RagAccountProperties props = new RagAccountProperties();
        props.setExportStorageDir(dir.toString());
        props.setExportTtlHours(48);
        assertEquals(dir, props.getExportStorageDir());
        assertEquals(48, props.getExportTtlHours());
    }

    @Test
    void ragRuntimePropertiesDefaultsAndMutation() {
        RagRuntimeProperties p = new RagRuntimeProperties();
        assertTrue(p.getWorkflowSchemaVersion().startsWith("1."));
        p.setAdvisorWithPostRetrieval(true);
        p.setMemoryMaxTurns(5);
        p.setMemoryMaxChars(100);
        assertTrue(p.isAdvisorWithPostRetrieval());
        assertEquals(5, p.getMemoryMaxTurns());
        assertEquals(100, p.getMemoryMaxChars());
    }
}

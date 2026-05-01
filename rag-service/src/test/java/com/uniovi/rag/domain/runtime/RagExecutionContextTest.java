package com.uniovi.rag.domain.runtime;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagExecutionContextTest {

    @Test
    void forLegacyPipeline_setsAllDocumentsSentinel() {
        RagConfig cfg = RagConfig.fromFeatureConfiguration(
                new RagFeatureConfiguration(), 5, 0.5, "m", "e", "c", "SIMPLE");
        RagExecutionContext ctx = RagExecutionContext.forLegacyPipeline(cfg, "t1");
        assertTrue(ctx.documentFilterIsAll());
        assertFalse(ctx.restrictsByProject());
    }

    @Test
    void restrictsByProject_whenProjectIdSet() {
        RagConfig cfg = RagConfig.fromFeatureConfiguration(
                new RagFeatureConfiguration(), 5, 0.5, "m", "e", "c", "SIMPLE");
        RagExecutionContext ctx =
                new RagExecutionContext(null, null, "p1", cfg, List.of(), "t");
        assertTrue(ctx.restrictsByProject());
    }

    @Test
    void documentFilterIsAll_whenContainsAllConstant() {
        RagConfig cfg = RagConfig.fromFeatureConfiguration(
                new RagFeatureConfiguration(), 5, 0.5, "m", "e", "c", "SIMPLE");
        RagExecutionContext ctx = new RagExecutionContext(
                null, null, null, cfg, List.of(RagExecutionContext.ALL_DOCUMENTS), "t");
        assertTrue(ctx.documentFilterIsAll());
    }
}

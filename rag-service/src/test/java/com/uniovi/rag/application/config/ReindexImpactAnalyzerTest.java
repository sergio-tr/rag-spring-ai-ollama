package com.uniovi.rag.application.config;

import com.uniovi.rag.domain.config.capability.Capability;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.indexing.ReindexImpactLevel;
import com.uniovi.rag.domain.config.runtime.ConfigProfileType;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReindexImpactAnalyzerTest {

    private final ReindexImpactAnalyzer analyzer = new ReindexImpactAnalyzer();

    private static CapabilitySet caps(boolean retrieval, String embeddingModel) {
        EnumSet<Capability> active = EnumSet.noneOf(Capability.class);
        if (retrieval) {
            active.add(Capability.USE_RETRIEVAL);
        }
        return new CapabilitySet(Set.copyOf(active), embeddingModel, "DEFAULT", "DEFAULT");
    }

    @Test
    void noSignalsYieldsNoReindex() {
        ReindexImpact impact =
                analyzer.analyze(caps(true, "e1"), caps(true, "e1"), Set.of());
        assertEquals(ReindexImpactLevel.NO_REINDEX, impact.level());
        assertTrue(impact.reasons().isEmpty());
    }

    @Test
    void metadataProfileOnlyYieldsSoftReindex() {
        ReindexImpact impact =
                analyzer.analyze(caps(true, "e1"), caps(true, "e1"), Set.of(ConfigProfileType.METADATA));
        assertEquals(ReindexImpactLevel.SOFT_REINDEX, impact.level());
        assertEquals(List.of("SOFT:METADATA"), impact.reasons());
    }

    @Test
    void embeddingProfileTouchYieldsHardReindex() {
        ReindexImpact impact =
                analyzer.analyze(caps(true, "e1"), caps(true, "e1"), Set.of(ConfigProfileType.EMBEDDING));
        assertEquals(ReindexImpactLevel.HARD_REINDEX, impact.level());
        assertTrue(impact.reasons().contains("HARD:EMBEDDING"));
    }

    @Test
    void capabilityEmbeddingDiffYieldsHardRegardlessOfProfileNames() {
        CapabilitySet before = caps(true, "model-a");
        CapabilitySet after = caps(true, "model-b");
        ReindexImpact impact = analyzer.analyze(before, after, Set.of());
        assertEquals(ReindexImpactLevel.HARD_REINDEX, impact.level());
        assertTrue(impact.reasons().contains("HARD:CAPABILITY_DIFF"));
    }

    @Test
    void hardProfileDominatesSoftMetadata() {
        ReindexImpact impact =
                analyzer.analyze(
                        caps(true, "e1"),
                        caps(true, "e1"),
                        Set.of(ConfigProfileType.METADATA, ConfigProfileType.CHUNKING));
        assertEquals(ReindexImpactLevel.HARD_REINDEX, impact.level());
        assertTrue(impact.reasons().stream().anyMatch(r -> r.startsWith("HARD:")));
    }
}

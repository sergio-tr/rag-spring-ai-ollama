package com.uniovi.rag.application.service.runtime.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.application.service.evaluation.preset.ExperimentalPresetCanonicalCatalog;
import com.uniovi.rag.domain.chat.IndexCompatibilityMessages;
import org.junit.jupiter.api.Test;

class IndexCompatibilityResultTest {

    @Test
    void checkStructuredSearchSnapshotBlocksRetrievalPresets() {
        var req =
                new ExperimentalPresetCanonicalCatalog.IndexRequirements(
                        ExperimentalPresetCanonicalCatalog.RequiredMaterialization.CHUNK_LEVEL, false);
        var snap = new IndexSnapshotCapabilities("STRUCTURED_SEARCH", false, "emb", 400, 40);

        IndexCompatibilityResult result = IndexCompatibilityResult.check(req, true, snap);

        assertThat(result.compatible()).isFalse();
        assertThat(result.reasonCode()).isEqualTo("STRUCTURED_SEARCH_RETRIEVAL_UNSUPPORTED");
        assertThat(result.message()).isEqualTo(IndexCompatibilityMessages.STRUCTURED_SEARCH_RETRIEVAL_UNSUPPORTED);
    }

    @Test
    void checkHybridSnapshotSatisfiesChunkLevelPreset() {
        var req =
                new ExperimentalPresetCanonicalCatalog.IndexRequirements(
                        ExperimentalPresetCanonicalCatalog.RequiredMaterialization.CHUNK_LEVEL, false);
        var snap = new IndexSnapshotCapabilities("HYBRID", true, "emb", 400, 40);

        IndexCompatibilityResult result = IndexCompatibilityResult.check(req, true, snap);

        assertThat(result.compatible()).isTrue();
    }

    @Test
    void checkMaterializationMismatchReturnsCanonicalMessage() {
        var req =
                new ExperimentalPresetCanonicalCatalog.IndexRequirements(
                        ExperimentalPresetCanonicalCatalog.RequiredMaterialization.HYBRID, false);
        var snap = new IndexSnapshotCapabilities("CHUNK_LEVEL", false, "emb", 400, 40);

        IndexCompatibilityResult result = IndexCompatibilityResult.check(req, true, snap);

        assertThat(result.compatible()).isFalse();
        assertThat(result.reasonCode()).isEqualTo("MATERIALIZATION_NOT_SUPPORTED");
        assertThat(result.message()).isEqualTo("Requires HYBRID index");
    }

    @Test
    void checkMetadataRequiredWhenSnapshotDoesNotSupportMetadata() {
        var req =
                new ExperimentalPresetCanonicalCatalog.IndexRequirements(
                        ExperimentalPresetCanonicalCatalog.RequiredMaterialization.CHUNK_LEVEL, true);
        var snap = new IndexSnapshotCapabilities("CHUNK_LEVEL", false, "emb", 400, 40);

        IndexCompatibilityResult result = IndexCompatibilityResult.check(req, true, snap);

        assertThat(result.compatible()).isFalse();
        assertThat(result.reasonCode()).isEqualTo("METADATA_SUPPORT_REQUIRED");
        assertThat(result.message()).isEqualTo(IndexCompatibilityMessages.METADATA_SUPPORT_REQUIRED);
    }

    @Test
    void checkNoActiveIndexUsesCanonicalMessage() {
        var req =
                new ExperimentalPresetCanonicalCatalog.IndexRequirements(
                        ExperimentalPresetCanonicalCatalog.RequiredMaterialization.CHUNK_LEVEL, false);

        IndexCompatibilityResult result = IndexCompatibilityResult.check(req, false, null);

        assertThat(result.compatible()).isFalse();
        assertThat(result.reasonCode()).isEqualTo("NO_ACTIVE_INDEX");
        assertThat(result.message()).isEqualTo(IndexCompatibilityMessages.NO_ACTIVE_INDEX);
    }
}

package com.uniovi.rag.application.service.evaluation.preset;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExperimentalPresetCanonicalCatalogMetadataTest {

    @Test
    void p0_isDirectLlmWithoutCorpus_p1_isFullCorpusSnapshotBacked() {
        assertThat(ExperimentalPresetCanonicalCatalog.corpusRequired(RagExperimentalPresetCode.P0)).isFalse();
        assertThat(ExperimentalPresetCanonicalCatalog.corpusRequired(RagExperimentalPresetCode.P1)).isTrue();
        assertThat(ExperimentalPresetCanonicalCatalog.requiresSnapshotForExecution(RagExperimentalPresetCode.P0)).isFalse();
        assertThat(ExperimentalPresetCanonicalCatalog.requiresSnapshotForExecution(RagExperimentalPresetCode.P1)).isTrue();
        assertThat(ExperimentalPresetCanonicalCatalog.requiresProjectDocuments(RagExperimentalPresetCode.P0)).isFalse();
        assertThat(ExperimentalPresetCanonicalCatalog.singleTurnBenchmarkSelectable(RagExperimentalPresetCode.P0)).isTrue();
        assertThat(ExperimentalPresetCanonicalCatalog.singleTurnBenchmarkSelectable(RagExperimentalPresetCode.P1)).isTrue();
    }

    @Test
    void p8_and_p12_productPresetIds_matchStablePresetUuids_noDriftWithP11Ordering() {
        assertThat(ExperimentalPresetCanonicalCatalog.productPresetId(RagExperimentalPresetCode.P8))
                .isEqualTo(UUID.fromString("cafe0001-0001-4001-8001-000000000018"));
        assertThat(ExperimentalPresetCanonicalCatalog.productPresetId(RagExperimentalPresetCode.P11))
                .isEqualTo(UUID.fromString("cafe0001-0001-4001-8001-000000000023"));
        assertThat(ExperimentalPresetCanonicalCatalog.productPresetId(RagExperimentalPresetCode.P12))
                .isEqualTo(UUID.fromString("cafe0001-0001-4001-8001-000000000024"));
    }

    @Test
    void p13_and_p14_areNotSingleTurnBenchmarkSelectable_butRemainInExperimentalCatalog() {
        assertThat(ExperimentalPresetCanonicalCatalog.singleTurnBenchmarkSelectable(RagExperimentalPresetCode.P13)).isFalse();
        assertThat(ExperimentalPresetCanonicalCatalog.singleTurnBenchmarkSelectable(RagExperimentalPresetCode.P14)).isFalse();
        assertThat(ExperimentalPresetCanonicalCatalog.requiresMultiTurn(RagExperimentalPresetCode.P13)).isTrue();
        assertThat(ExperimentalPresetCanonicalCatalog.requiresMultiTurn(RagExperimentalPresetCode.P14)).isTrue();
        assertThat(ExperimentalPresetCanonicalCatalog.corpusRequired(RagExperimentalPresetCode.P13)).isTrue();
    }
}

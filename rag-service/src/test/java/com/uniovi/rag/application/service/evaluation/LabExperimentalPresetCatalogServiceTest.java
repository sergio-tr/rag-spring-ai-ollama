package com.uniovi.rag.application.service.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.evaluation.workbook.EvaluationReferenceBundleLoader;
import com.uniovi.rag.application.evaluation.workbook.ReferenceBundleCounts;
import com.uniovi.rag.application.evaluation.workbook.ReferenceBundleSnapshot;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.domain.evaluation.workbook.EvaluationWorkbook;
import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import com.uniovi.rag.domain.evaluation.workbook.RagPresetDefinition;
import com.uniovi.rag.domain.evaluation.workbook.ValidationReport;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LabExperimentalPresetCatalogServiceTest {

    @Test
    void list_returnsP0ToP14_andMarksMultiTurnWithoutBlockingChatSelection() {
        EvaluationReferenceBundleLoader loader = mock(EvaluationReferenceBundleLoader.class);
        EvaluationWorkbook wb = EvaluationWorkbook.builder()
                .ragPresetCatalog(List.of(
                        new RagPresetDefinition(
                                RagExperimentalPresetCode.P0,
                                "baseline",
                                "Direct LLM",
                                "off",
                                "",
                                "",
                                "",
                                "",
                                "",
                                "No retrieval",
                                ""),
                        new RagPresetDefinition(
                                RagExperimentalPresetCode.P13,
                                "conversational",
                                "Clarification loop",
                                "on",
                                "",
                                "",
                                "",
                                "",
                                "",
                                "Requires multi-turn harness",
                                "")))
                .build();
        when(loader.getSnapshot())
                .thenReturn(new ReferenceBundleSnapshot(true, wb, new ValidationReport(), ReferenceBundleCounts.empty(), Optional.empty(), Optional.of("abc"), 123));
        LabExperimentalPresetCatalogService cut = new LabExperimentalPresetCatalogService(loader, new RagFeatureConfiguration());

        var rows = cut.list();

        assertThat(rows).hasSize(15);
        assertThat(rows).extracting("code").contains("P0", "P14");
        assertThat(rows).extracting("code").doesNotHaveDuplicates();
        assertThat(rows).allMatch(r -> r.productPresetId() != null && !r.productPresetId().isBlank());
        assertThat(rows).allMatch(r -> r.labOnly() == (r.labSelectable() && !r.chatSelectable()));
        var p0 = rows.stream().filter(r -> "P0".equals(r.code())).findFirst().orElseThrow();
        assertThat(p0.corpusRequired()).isFalse();
        assertThat(p0.requiresSnapshot()).isFalse();
        assertThat(p0.requiresProjectDocuments()).isFalse();
        assertThat(p0.singleTurnBenchmarkSelectable()).isTrue();
        assertThat(p0.protocolStageIndex()).isZero();
        assertThat(p0.parentPresetCode()).isNull();
        assertThat(p0.effectiveTerminalRuntimeJson()).isNotBlank().contains("useRetrieval");

        var p3 = rows.stream().filter(r -> "P3".equals(r.code())).findFirst().orElseThrow();
        assertThat(p3.protocolStageIndex()).isEqualTo(3);
        assertThat(p3.parentPresetCode()).isEqualTo("P2");

        var p1 = rows.stream().filter(r -> "P1".equals(r.code())).findFirst().orElseThrow();
        assertThat(p1.corpusRequired()).isTrue();
        assertThat(p1.requiresSnapshot()).isTrue();
        assertThat(p1.singleTurnBenchmarkSelectable()).isTrue();

        var p13 = rows.stream().filter(r -> "P13".equals(r.code())).findFirst().orElseThrow();
        assertThat(p13.supported()).isTrue();
        assertThat(p13.supportStatus()).isEqualTo("FUTURE_MULTI_TURN_NOT_SELECTABLE");
        assertThat(p13.reasonIfUnsupported()).isNull();
        assertThat(p13.chatSelectable()).isTrue();
        assertThat(p13.requiresMultiTurn()).isTrue();
        assertThat(p13.singleTurnBenchmarkSelectable()).isFalse();

        var p14 = rows.stream().filter(r -> "P14".equals(r.code())).findFirst().orElseThrow();
        assertThat(p14.chatSelectable()).isTrue();
        assertThat(p14.requiresMultiTurn()).isTrue();
        assertThat(p14.singleTurnBenchmarkSelectable()).isFalse();

        var p11 = rows.stream().filter(r -> "P11".equals(r.code())).findFirst().orElseThrow();
        var p12 = rows.stream().filter(r -> "P12".equals(r.code())).findFirst().orElseThrow();
        assertThat(p11.requiresMultiTurn()).isFalse();
        assertThat(p12.requiresMultiTurn()).isFalse();
        assertThat(p11.singleTurnBenchmarkSelectable()).isFalse();
        assertThat(p12.singleTurnBenchmarkSelectable()).isFalse();
        assertThat(p11.labSelectable()).isFalse();
        assertThat(p12.labSelectable()).isFalse();
        assertThat(p11.supportStatus()).isEqualTo("NOT_COMPARABLE_IN_SINGLE_TURN_LAB");
        assertThat(p12.supportStatus()).isEqualTo("NOT_COMPARABLE_IN_SINGLE_TURN_LAB");
        assertThat(p11.chatSelectable()).isTrue();
        assertThat(p12.chatSelectable()).isTrue();

        // Post-retrieval is implemented, so P8 can be chat-selectable (unless blocked for other reasons).
        var p6 = rows.stream().filter(r -> "P6".equals(r.code())).findFirst().orElseThrow();
        var p8 = rows.stream().filter(r -> "P8".equals(r.code())).findFirst().orElseThrow();
        assertThat(p6.chatSelectable()).isTrue();
        assertThat(p8.chatSelectable()).isTrue();
        assertThat(p6.supported()).isTrue();
        assertThat(p8.supported()).isTrue();
        assertThat(p6.supportStatus()).isEqualTo("EXECUTABLE");
        assertThat(p8.supportStatus()).isEqualTo("EXECUTABLE");
        assertThat(p6.reasonIfUnsupported()).isNull();
        assertThat(p8.reasonIfUnsupported()).isNull();
    }
}

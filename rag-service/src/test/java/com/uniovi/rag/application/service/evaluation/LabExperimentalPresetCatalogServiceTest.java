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
    void list_returnsP0ToP14_andMarksMultiTurnAsNotSupported() {
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
                                RagExperimentalPresetCode.P11,
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
        assertThat(rows).allMatch(r -> r.productPresetId() != null && !r.productPresetId().isBlank());
        var p11 = rows.stream().filter(r -> "P11".equals(r.code())).findFirst().orElseThrow();
        assertThat(p11.supported()).isFalse();
        assertThat(p11.supportStatus()).isEqualTo("REQUIRES_MULTI_TURN");
        assertThat(p11.reasonIfUnsupported()).isEqualTo("PRESET_CLARIFICATION_BENCHMARK_NOT_SUPPORTED");
        assertThat(p11.chatSelectable()).isFalse();

        var p12 = rows.stream().filter(r -> "P12".equals(r.code())).findFirst().orElseThrow();
        assertThat(p12.chatSelectable()).isFalse();

        // Post-retrieval is implemented, so P8 can be chat-selectable (unless blocked for other reasons).
        var p6 = rows.stream().filter(r -> "P6".equals(r.code())).findFirst().orElseThrow();
        var p8 = rows.stream().filter(r -> "P8".equals(r.code())).findFirst().orElseThrow();
        assertThat(p6.chatSelectable()).isTrue();
        assertThat(p8.chatSelectable()).isTrue();
    }
}

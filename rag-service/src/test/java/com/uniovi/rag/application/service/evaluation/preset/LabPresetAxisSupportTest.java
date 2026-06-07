package com.uniovi.rag.application.service.evaluation.preset;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.application.evaluation.workbook.EvaluationReferenceBundleLoader;
import com.uniovi.rag.application.evaluation.workbook.EvaluationWorkbookParser;
import com.uniovi.rag.application.service.evaluation.BenchmarkRunOrchestrator;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationCampaignEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LabPresetAxisSupportTest {

    private LabPresetAxisSupport support;

    @BeforeEach
    void setUp() {
        support = new LabPresetAxisSupport(new EvaluationReferenceBundleLoader(new EvaluationWorkbookParser()));
    }

    @Test
    void comparisonLabel_usesPresetCatalogName_notModelId() {
        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setLlmModelId("gemma3:4b");
        support.enrichRagPresetChildRun(run, UUID.randomUUID(), "P0");

        assertThat(support.resolvePresetCode(run)).isEqualTo("P0");
        assertThat(support.comparisonLabel(run)).startsWith("P0");
        assertThat(support.comparisonLabel(run)).doesNotContain("gemma3:4b");
    }

    @Test
    void enrichRagPresetChildRun_persistsPresetAxisMetadata() {
        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setBenchmarkKind(BenchmarkKind.RAG_PRESET_END_TO_END.name());
        run.setLlmModelId("gemma3:4b");
        UUID campaignId = UUID.randomUUID();
        support.enrichRagPresetChildRun(run, campaignId, "P2");

        Map<String, Object> agg = run.getAggregatesJson();
        assertThat(agg.get(LabPresetAxisSupport.AGG_KEY_PRESET_KEY)).isEqualTo("P2");
        assertThat(agg.get(LabPresetAxisSupport.AGG_KEY_COMPARISON_AXIS))
                .isEqualTo(LabPresetAxisSupport.COMPARISON_AXIS_PRESET);
        assertThat(agg.get("campaignId")).isEqualTo(campaignId.toString());
        assertThat(agg.get("modelId")).isEqualTo("gemma3:4b");
        assertThat(agg.get(BenchmarkRunOrchestrator.AGG_KEY_REQUESTED_PRESET_CODES)).isEqualTo(List.of("P2"));
    }

    @Test
    void isRagPresetCampaignRun_detectsCampaignChildRuns() {
        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setBenchmarkKind(BenchmarkKind.RAG_PRESET_END_TO_END.name());
        run.setCampaign(new EvaluationCampaignEntity());
        assertThat(LabPresetAxisSupport.isRagPresetCampaignRun(run)).isTrue();
    }
}

package com.uniovi.rag.application.service.evaluation.metrics;

import com.uniovi.rag.domain.evaluation.workbook.RagPresetQuestion;
import com.uniovi.rag.domain.model.QueryType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DatasetQuestionSubsetSupportTest {

    @Test
    void filterQuestions_preservesOrderAndFailsOnUnknownId() {
        RagPresetQuestion q1 = question("RAG-001");
        RagPresetQuestion q2 = question("RAG-002");
        DatasetQuestionSubsetSupport.ResolvedSubset subset =
                new DatasetQuestionSubsetSupport.ResolvedSubset(
                        DatasetQuestionSubsetSupport.FILTER_EXPLICIT_IDS,
                        null,
                        null,
                        null,
                        List.of("RAG-002", "RAG-001"));

        List<RagPresetQuestion> filtered =
                DatasetQuestionSubsetSupport.filterQuestions(List.of(q1, q2), subset);

        assertThat(filtered).extracting(RagPresetQuestion::id).containsExactly("RAG-002", "RAG-001");
    }

    @Test
    void goldSubsetManifest_resolvesQuestionIds() {
        GoldSubsetManifest manifest = GoldSubsetManifestLoader.load(GoldSubsetManifestLoader.GOLD_SUBSET_V1);
        DatasetQuestionSubsetSupport.ResolvedSubset subset =
                DatasetQuestionSubsetSupport.resolve(
                        new com.uniovi.rag.application.service.evaluation.StartBenchmarkRunRequest(
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                List.of(),
                                List.of(),
                                GoldSubsetManifestLoader.GOLD_SUBSET_V1,
                                null));

        assertThat(subset.filterMode()).isEqualTo(DatasetQuestionSubsetSupport.FILTER_GOLD_SUBSET);
        assertThat(subset.questionIds()).hasSize(18);
        assertThat(subset.subsetId()).isEqualTo(manifest.manifestId());
    }

    @Test
    void resolvedExpectedItemCount_usesFilteredSubsetWhenPresent() {
        Map<String, Object> agg = new LinkedHashMap<>();
        agg.put(DatasetQuestionSubsetSupport.AGG_KEY_DATASET_QUESTION_FILTER, DatasetQuestionSubsetSupport.FILTER_GOLD_SUBSET);
        agg.put(DatasetQuestionSubsetSupport.AGG_KEY_FILTERED_QUESTION_COUNT, 18);

        assertThat(DatasetQuestionSubsetSupport.resolvedExpectedItemCount(agg, 60, 1)).isEqualTo(18);
        assertThat(DatasetQuestionSubsetSupport.resolvedExpectedItemCount(agg, 60, 2)).isEqualTo(36);
    }

    @Test
    void resolvedItemCount_goldSubsetMini_is18NotFullDataset() {
        int subsetQuestions =
                DatasetQuestionSubsetSupport.resolvedItemCount(
                        new com.uniovi.rag.application.service.evaluation.StartBenchmarkRunRequest(
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                List.of(),
                                List.of(),
                                GoldSubsetManifestLoader.GOLD_SUBSET_V1,
                                null));
        assertThat(subsetQuestions).isEqualTo(18);
        assertThat(DatasetQuestionSubsetSupport.resolvedExpectedItemCount(Map.of(), 60, 1)).isEqualTo(60);
        Map<String, Object> agg = Map.of(DatasetQuestionSubsetSupport.AGG_KEY_FILTERED_QUESTION_COUNT, subsetQuestions);
        assertThat(DatasetQuestionSubsetSupport.resolvedExpectedItemCount(agg, 60, 1)).isEqualTo(18);
    }

    @Test
    void resolvedExpectedItemCount_fallsBackToFullDatasetWhenNoSubset() {
        assertThat(DatasetQuestionSubsetSupport.resolvedExpectedItemCount(Map.of(), 60, 1)).isEqualTo(60);
    }

    @Test
    void goldSubsetManifest_enrichesAnswerabilityFromManifestEntry() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        boolean applied =
                DatasetQuestionSubsetSupport.enrichAnswerabilityFromGoldManifest(
                        metrics, "RAG-003", GoldSubsetManifestLoader.GOLD_SUBSET_V1);

        assertThat(applied).isTrue();
        assertThat(metrics.get(DatasetMetricContract.KEY_ANSWERABILITY)).isEqualTo("UNANSWERABLE");
        assertThat(metrics.get(DatasetMetricContract.KEY_ANSWERABILITY_SOURCE))
                .isEqualTo(AnswerabilitySource.GOLD_SUBSET_MANIFEST.name());
    }

    @Test
    void enrichAnswerabilityFromPersistedSubset_usesSubsetIdOnMetrics() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("subsetId", GoldSubsetManifestLoader.GOLD_SUBSET_V1);

        boolean applied =
                DatasetQuestionSubsetSupport.enrichAnswerabilityFromPersistedSubset(metrics, "RAG-001");

        assertThat(applied).isTrue();
        assertThat(metrics.get(DatasetMetricContract.KEY_ANSWERABILITY)).isEqualTo("ANSWERABLE");
    }

    private static RagPresetQuestion question(String id) {
        return new RagPresetQuestion(
                id,
                "q",
                "answer",
                Optional.of(QueryType.BOOLEAN_QUERY),
                Optional.empty(),
                "",
                List.of(),
                List.of(),
                "",
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                "");
    }
}

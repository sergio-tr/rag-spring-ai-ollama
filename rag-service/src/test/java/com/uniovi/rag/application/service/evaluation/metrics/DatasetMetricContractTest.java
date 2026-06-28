package com.uniovi.rag.application.service.evaluation.metrics;

import com.uniovi.rag.domain.evaluation.workbook.RagPresetQuestion;
import com.uniovi.rag.domain.model.QueryType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DatasetMetricContractTest {

    @Test
    void enrichFromQuestion_setsUnknownWhenUnanswerableColumnMissing() {
        RagPresetQuestion q =
                new RagPresetQuestion(
                        "q1",
                        "question",
                        "answer",
                        Optional.of(QueryType.BOOLEAN_QUERY),
                        Optional.empty(),
                        "",
                        List.of("ACTA_1"),
                        List.of("CHUNK_1"),
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
        Map<String, Object> metrics = new LinkedHashMap<>();
        DatasetMetricContract.enrichFromQuestion(metrics, q);

        assertThat(metrics.get(DatasetMetricContract.KEY_ANSWERABILITY)).isEqualTo(Answerability.UNKNOWN.name());
        assertThat(metrics.get(DatasetMetricContract.KEY_ANSWERABILITY_SOURCE))
                .isEqualTo(AnswerabilitySource.DEFAULT_UNKNOWN.name());
        assertThat(metrics.get(DatasetMetricContract.KEY_QUERY_TYPE_EXPECTED)).isEqualTo("BOOLEAN_QUERY");
        assertThat(metrics.get(DatasetMetricContract.KEY_GOLD_DOCUMENT_IDS)).isEqualTo(List.of("ACTA_1"));
        assertThat(metrics.get(DatasetMetricContract.KEY_GOLD_CHUNK_IDS)).isEqualTo(List.of("CHUNK_1"));
    }

    @Test
    void enrichFromQuestion_setsAnswerableWhenDeclaredFalse() {
        RagPresetQuestion q =
                new RagPresetQuestion(
                        "q2",
                        "question",
                        "answer",
                        Optional.empty(),
                        Optional.empty(),
                        "",
                        List.of(),
                        List.of(),
                        "",
                        false,
                        true,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        "");
        Map<String, Object> metrics = new LinkedHashMap<>();
        DatasetMetricContract.enrichFromQuestion(metrics, q);
        assertThat(metrics.get(DatasetMetricContract.KEY_ANSWERABILITY)).isEqualTo(Answerability.ANSWERABLE.name());
        assertThat(metrics.get(DatasetMetricContract.KEY_ANSWERABILITY_SOURCE))
                .isEqualTo(AnswerabilitySource.DATASET_COLUMN.name());
    }

    @Test
    void enrichFromQuestion_setsAmbiguousWhenDeclaredTrue() {
        RagPresetQuestion q =
                new RagPresetQuestion(
                        "q3",
                        "question",
                        "",
                        Optional.empty(),
                        Optional.empty(),
                        "",
                        List.of(),
                        List.of(),
                        "",
                        false,
                        true,
                        true,
                        true,
                        false,
                        false,
                        false,
                        false,
                        "");
        Map<String, Object> metrics = new LinkedHashMap<>();
        DatasetMetricContract.enrichFromQuestion(metrics, q);
        assertThat(metrics.get(DatasetMetricContract.KEY_ANSWERABILITY)).isEqualTo(Answerability.AMBIGUOUS.name());
    }

    @Test
    void hasGoldLabels_falseWhenOnlyNoneTokens() {
        Map<String, Object> metrics = Map.of(DatasetMetricContract.KEY_GOLD_DOCUMENT_IDS, List.of("NONE", ""));
        assertThat(DatasetMetricContract.hasGoldLabels(metrics)).isFalse();
    }

    @Test
    void hasGoldLabels_trueWhenChunkIdsPresent() {
        Map<String, Object> metrics = Map.of("gold_chunk_ids", List.of("ACTA_1:0"));
        assertThat(DatasetMetricContract.hasGoldLabels(metrics)).isTrue();
    }

    @Test
    void ensureQueryTypeExpected_usesEntityFallbackWhenContractMissing() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        DatasetMetricContract.ensureQueryTypeExpected(metrics, "COUNT_DOCUMENTS");
        assertThat(metrics.get(DatasetMetricContract.KEY_QUERY_TYPE_EXPECTED)).isEqualTo("COUNT_DOCUMENTS");
    }

    @Test
    void ensureQueryTypeExpected_usesLegacyQueryTypeField() {
        Map<String, Object> metrics = new LinkedHashMap<>(Map.of("query_type", "BOOLEAN_QUERY"));
        DatasetMetricContract.ensureQueryTypeExpected(metrics);
        assertThat(metrics.get(DatasetMetricContract.KEY_QUERY_TYPE_EXPECTED)).isEqualTo("BOOLEAN_QUERY");
    }

    @Test
    void enrichFromQuestion_infersAnswerableFromExpectedAnswer() {
        RagPresetQuestion q =
                new RagPresetQuestion(
                        "q4",
                        "question",
                        "Asistieron 12 personas.",
                        Optional.of(QueryType.COUNT_DOCUMENTS),
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
        Map<String, Object> metrics = new LinkedHashMap<>();
        DatasetMetricContract.enrichFromQuestion(metrics, q, "abc123");
        assertThat(metrics.get(DatasetMetricContract.KEY_ANSWERABILITY)).isEqualTo(Answerability.ANSWERABLE.name());
        assertThat(metrics.get(DatasetMetricContract.KEY_ANSWERABILITY_SOURCE))
                .isEqualTo(AnswerabilitySource.INFERRED_FROM_EXPECTED_ANSWER.name());
        assertThat(metrics.get(DatasetMetricContract.KEY_ANSWERABILITY_RULE_ID)).isEqualTo(AnswerabilityLabelRules.POS_FACTUAL);
        assertThat(metrics.get(DatasetMetricContract.KEY_LABELLED_DATASET_SHA256)).isEqualTo("abc123");
    }

    @Test
    void enrichFromQuestion_setsVersionMetadata() {
        RagPresetQuestion q =
                new RagPresetQuestion(
                        "q5",
                        "question",
                        "No hay actas.",
                        Optional.empty(),
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
        Map<String, Object> metrics = new LinkedHashMap<>();
        DatasetMetricContract.enrichFromQuestion(metrics, q);
        assertThat(metrics.get(DatasetMetricContract.KEY_ANSWERABILITY_RULES_VERSION))
                .isEqualTo(AnswerabilityLabelingService.rulesVersion());
    }

    @Test
    void mergeRowQueryType_copiesFromEvaluationRow() {
        Map<String, Object> row = Map.of("query_type", "GET_FIELD");
        Map<String, Object> metrics = new LinkedHashMap<>();
        DatasetMetricContract.mergeRowQueryType(row, metrics);
        assertThat(metrics.get(DatasetMetricContract.KEY_QUERY_TYPE_EXPECTED)).isEqualTo("GET_FIELD");
    }
}

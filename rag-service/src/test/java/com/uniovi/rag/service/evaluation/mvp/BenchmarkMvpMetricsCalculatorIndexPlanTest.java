package com.uniovi.rag.service.evaluation.mvp;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.application.service.evaluation.BenchmarkResultRowKeys;
import com.uniovi.rag.domain.evaluation.BenchmarkItemOutcome;
import com.uniovi.rag.application.service.evaluation.LabEvaluationRunService;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationResultEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BenchmarkMvpMetricsCalculatorIndexPlanTest {

    @Test
    void csv_row_includes_run_plan_and_skip_fields() {
        EvaluationRunEntity run = new EvaluationRunEntity();
        EvaluationResultEntity item = new EvaluationResultEntity();
        item.setBenchmarkKind("RAG_PRESET_END_TO_END");
        item.setMetricsPayload(
                new LinkedHashMap<>(
                        Map.of(
                                BenchmarkResultRowKeys.ITEM_OUTCOME,
                                BenchmarkItemOutcome.SKIPPED.name(),
                                BenchmarkResultRowKeys.ERROR_CODE,
                                "NO_ACTIVE_INDEX",
                                BenchmarkResultRowKeys.REASON,
                                "No active index snapshot.",
                                "groupKey",
                                "DOCUMENT_LEVEL",
                                "runPlanVersion",
                                1)));

        Map<String, String> row = BenchmarkMvpMetricsCalculator.computeMvpFlatCsvRow(item, run);
        assertThat(row.get("groupKey")).isEqualTo("DOCUMENT_LEVEL");
        assertThat(row.get("skipReasonCode")).isEqualTo("NO_ACTIVE_INDEX");
        assertThat(row.get("skipReason")).contains("No active index");
        assertThat(row.get("runPlanVersion")).isEqualTo("1");
    }

    @Test
    void csv_row_includes_auto_reindex_operational_fields_when_present() {
        EvaluationRunEntity run = new EvaluationRunEntity();
        EvaluationResultEntity item = new EvaluationResultEntity();
        item.setBenchmarkKind("RAG_PRESET_END_TO_END");
        UUID snapId = UUID.randomUUID();
        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put("groupKey", "HYBRID_METADATA");
        mp.put("runPlanVersion", 1);
        mp.put("indexCompatibilityStatus", "COMPATIBLE");
        mp.put("effectiveGroupSnapshotId", snapId.toString());
        mp.put("groupIndexProfileHash", "h1");
        mp.put("reindexAction", "BUILD_AND_ACTIVATE");
        mp.put("reindexStatus", "BUILT");
        mp.put("forcedSnapshotSelection", true);
        mp.put("reindexEventId", UUID.randomUUID().toString());
        mp.put("reindexStartedAt", "2026-05-08T00:00:00Z");
        mp.put("reindexCompletedAt", "2026-05-08T00:00:01Z");
        mp.put("reindexErrorCode", "");
        mp.put("reindexErrorReason", "");
        item.setMetricsPayload(new LinkedHashMap<>(mp));

        Map<String, String> row = BenchmarkMvpMetricsCalculator.computeMvpFlatCsvRow(item, run);
        assertThat(row.get("effectiveGroupSnapshotId")).isEqualTo(snapId.toString());
        assertThat(row.get("reindexAction")).isEqualTo("BUILD_AND_ACTIVATE");
        assertThat(row.get("reindexStatus")).isEqualTo("BUILT");
        assertThat(row.get("forcedSnapshotSelection")).isEqualTo("true");
        assertThat(row.get("groupIndexProfileHash")).isEqualTo("h1");
    }

    @Test
    void csv_row_with_legacy_metrics_payload_does_not_fail_and_emits_empty_fields() {
        EvaluationRunEntity run = new EvaluationRunEntity();
        EvaluationResultEntity item = new EvaluationResultEntity();
        item.setBenchmarkKind("RAG_PRESET_END_TO_END");
        item.setMetricsPayload(new LinkedHashMap<>(Map.of("groupKey", "NO_INDEX")));

        Map<String, String> row = BenchmarkMvpMetricsCalculator.computeMvpFlatCsvRow(item, run);
        assertThat(row.get("effectiveGroupSnapshotId")).isEqualTo("");
        assertThat(row.get("reindexAction")).isEqualTo("");
        assertThat(row.get("reindexStatus")).isEqualTo("");
        assertThat(row.get("forcedSnapshotSelection")).isEqualTo("");
    }

    @Test
    void mvp_csv_columns_include_new_auto_reindex_fields_in_stable_order() {
        assertThat(LabEvaluationRunService.MVP_ITEMS_CSV_COLUMNS_FOR_TESTS())
                .containsSubsequence(
                        "indexProfileHash",
                        "effectiveGroupSnapshotId",
                        "groupIndexProfileHash",
                        "reindexAction",
                        "reindexStatus",
                        "forcedSnapshotSelection",
                        "reindexEventId",
                        "reindexStartedAt",
                        "reindexCompletedAt",
                        "reindexErrorCode",
                        "reindexErrorReason",
                        "presetIndexRequirements",
                        "activeSnapshotCapabilities");
    }
}


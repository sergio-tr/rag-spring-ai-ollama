package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.domain.evaluation.BenchmarkItemOutcome;
import com.uniovi.rag.infrastructure.persistence.EvaluationResultRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationResultEntity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Guards Lab benchmark completion: jobs must not succeed with zero executed items, unexplained skips,
 * or outcome counts that do not reconcile with the planned workload.
 */
@Service
public class LabBenchmarkEvidenceValidator {

    public static final String FAILURE_CODE_NO_EXECUTABLE_ITEMS = "BENCHMARK_NO_EXECUTABLE_ITEMS";
    public static final String FAILURE_CODE_ALL_SKIPPED = "BENCHMARK_ALL_ITEMS_SKIPPED";
    public static final String FAILURE_CODE_NO_EXECUTED_ITEMS = "COMPLETED_WITH_NO_EXECUTED_ITEMS";
    public static final String FAILURE_CODE_FAILED_VALIDATION = "FAILED_VALIDATION";
    public static final String FAILURE_CODE_SKIPPED_WITHOUT_REASON = "BENCHMARK_SKIPPED_WITHOUT_REASON";
    public static final String FAILURE_CODE_NOT_SUPPORTED_WITHOUT_REASON =
            "BENCHMARK_NOT_SUPPORTED_WITHOUT_REASON";

    private static final String AGG_EXPECTED_ITEM_COUNT = "expectedItemCount";

    private final EvaluationResultRepository evaluationResultRepository;
    private final EvaluationRunRepository evaluationRunRepository;

    public LabBenchmarkEvidenceValidator(
            EvaluationResultRepository evaluationResultRepository,
            EvaluationRunRepository evaluationRunRepository) {
        this.evaluationResultRepository = evaluationResultRepository;
        this.evaluationRunRepository = evaluationRunRepository;
    }

    /**
     * @return empty when the run may succeed; otherwise failure code and message.
     */
    public Optional<EvidenceFailure> validateRun(UUID evaluationRunId) {
        if (evaluationRunId == null) {
            return Optional.of(
                    new EvidenceFailure(FAILURE_CODE_NO_EXECUTABLE_ITEMS, "Benchmark produced no result rows."));
        }
        List<EvaluationResultEntity> items =
                evaluationResultRepository.findByRun_IdOrderByEvaluatedAtAsc(evaluationRunId);
        if (items.isEmpty()) {
            return Optional.of(
                    new EvidenceFailure(
                            FAILURE_CODE_NO_EXECUTABLE_ITEMS, "Benchmark finished without any result rows."));
        }
        long expected = resolveExpectedItemCount(evaluationRunId, items.size());
        RagBenchmarkOutcomeTally tally = RagBenchmarkOutcomeTally.fromResultRows(items, expected);
        return validateTally(tally);
    }

    public Optional<EvidenceFailure> validateCampaignRuns(List<UUID> runIds) {
        if (runIds == null || runIds.isEmpty()) {
            return Optional.of(new EvidenceFailure(FAILURE_CODE_NO_EXECUTABLE_ITEMS, "Campaign produced no runs."));
        }
        long totalExpected = 0;
        long totalExecuted = 0;
        long totalFailed = 0;
        long totalSkipped = 0;
        long totalNotSupported = 0;
        long totalRows = 0;
        boolean skippedMissing = false;
        boolean notSupportedMissing = false;

        for (UUID runId : runIds) {
            List<EvaluationResultEntity> items =
                    evaluationResultRepository.findByRun_IdOrderByEvaluatedAtAsc(runId);
            long expected = resolveExpectedItemCount(runId, items.size());
            RagBenchmarkOutcomeTally tally = RagBenchmarkOutcomeTally.fromResultRows(items, expected);
            totalExpected += tally.expectedItems();
            totalExecuted += tally.executed();
            totalFailed += tally.failed();
            totalSkipped += tally.skipped();
            totalNotSupported += tally.notSupported();
            totalRows += tally.totalRows();
            skippedMissing = skippedMissing || tally.skippedMissingReason();
            notSupportedMissing = notSupportedMissing || tally.notSupportedMissingReason();
        }

        RagBenchmarkOutcomeTally merged =
                new RagBenchmarkOutcomeTally(
                        totalExpected > 0 ? totalExpected : totalRows,
                        totalExecuted,
                        totalFailed,
                        totalSkipped,
                        totalNotSupported,
                        0,
                        0,
                        totalRows,
                        skippedMissing,
                        notSupportedMissing);
        return validateTally(merged);
    }

    /** Closure map attached to async task result on successful validation. */
    public Map<String, Object> closureForRun(UUID evaluationRunId) {
        List<EvaluationResultEntity> items =
                evaluationResultRepository.findByRun_IdOrderByEvaluatedAtAsc(evaluationRunId);
        long expected = resolveExpectedItemCount(evaluationRunId, items.size());
        return RagBenchmarkOutcomeTally.fromResultRows(items, expected).toClosureMap();
    }

    /** Merged closure for multi-run campaign coordinator tasks. */
    public Map<String, Object> closureForCampaignRuns(List<UUID> runIds) {
        if (runIds == null || runIds.isEmpty()) {
            return Map.of();
        }
        long totalExpected = 0;
        long totalExecuted = 0;
        long totalFailed = 0;
        long totalSkipped = 0;
        long totalNotSupported = 0;
        long totalRows = 0;
        for (UUID runId : runIds) {
            List<EvaluationResultEntity> items =
                    evaluationResultRepository.findByRun_IdOrderByEvaluatedAtAsc(runId);
            long expected = resolveExpectedItemCount(runId, items.size());
            RagBenchmarkOutcomeTally tally = RagBenchmarkOutcomeTally.fromResultRows(items, expected);
            totalExpected += tally.expectedItems();
            totalExecuted += tally.executed();
            totalFailed += tally.failed();
            totalSkipped += tally.skipped();
            totalNotSupported += tally.notSupported();
            totalRows += tally.totalRows();
        }
        RagBenchmarkOutcomeTally merged =
                new RagBenchmarkOutcomeTally(
                        totalExpected > 0 ? totalExpected : totalRows,
                        totalExecuted,
                        totalFailed,
                        totalSkipped,
                        totalNotSupported,
                        0,
                        0,
                        totalRows,
                        false,
                        false);
        return merged.toClosureMap();
    }

    private Optional<EvidenceFailure> validateTally(RagBenchmarkOutcomeTally tally) {
        if (tally.skippedMissingReason()) {
            return Optional.of(
                    new EvidenceFailure(
                            FAILURE_CODE_SKIPPED_WITHOUT_REASON,
                            "One or more skipped items are missing a skip reason — check export rows."));
        }
        if (tally.notSupportedMissingReason()) {
            return Optional.of(
                    new EvidenceFailure(
                            FAILURE_CODE_NOT_SUPPORTED_WITHOUT_REASON,
                            "One or more not-supported items are missing a reason code."));
        }
        if (tally.expectedItems() > 0 && tally.accountedItems() != tally.expectedItems()) {
            return Optional.of(
                    new EvidenceFailure(
                            FAILURE_CODE_FAILED_VALIDATION,
                            "Benchmark item count mismatch: expected "
                                    + tally.expectedItems()
                                    + " but accounted "
                                    + tally.accountedItems()
                                    + " (executed="
                                    + tally.executed()
                                    + ", failed="
                                    + tally.failed()
                                    + ", skipped="
                                    + tally.skipped()
                                    + ", notSupported="
                                    + tally.notSupported()
                                    + ")."));
        }
        if (tally.executed() <= 0 && tally.expectedItems() > 0) {
            if (tally.skipped() > 0 && tally.skipped() == tally.accountedItems()) {
                return Optional.of(
                        new EvidenceFailure(
                                FAILURE_CODE_ALL_SKIPPED,
                                "Every benchmark item was skipped — check knowledge base, models, and preset compatibility."));
            }
            if (tally.notSupported() > 0 && tally.notSupported() == tally.accountedItems()) {
                return Optional.of(
                        new EvidenceFailure(
                                FAILURE_CODE_NO_EXECUTED_ITEMS,
                                "Every benchmark item was not supported — check presets and index compatibility."));
            }
            return Optional.of(
                    new EvidenceFailure(
                            FAILURE_CODE_NO_EXECUTED_ITEMS,
                            "No benchmark items executed — the run cannot be reported as a successful evaluation."));
        }
        return Optional.empty();
    }

    private long resolveExpectedItemCount(UUID evaluationRunId, int fallbackRowCount) {
        return evaluationRunRepository
                .findAggregatesJsonByRunId(evaluationRunId)
                .map(LabBenchmarkEvidenceValidator::readExpectedFromAggregates)
                .filter(n -> n > 0)
                .orElse((long) fallbackRowCount);
    }

    @SuppressWarnings("unchecked")
    private static long readExpectedFromAggregates(Map<String, Object> agg) {
        if (agg == null || agg.isEmpty()) {
            return 0;
        }
        Object direct = agg.get(AGG_EXPECTED_ITEM_COUNT);
        if (direct instanceof Number n) {
            return Math.max(0, n.longValue());
        }
        Object planned = agg.get("plannedTotalItems");
        if (planned instanceof Number n) {
            return Math.max(0, n.longValue());
        }
        return 0;
    }

    public record EvidenceFailure(String failureCode, String message) {

        public Map<String, Object> toResultMeta() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("failureCode", failureCode);
            m.put("message", message);
            return Map.copyOf(m);
        }
    }
}

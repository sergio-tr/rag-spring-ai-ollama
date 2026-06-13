package com.uniovi.rag.application.service.evaluation.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.evaluation.BenchmarkItemOutcome;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BenchmarkMvpRollupCalculatorToolTest {

    @Test
    void computesToolRatesOnExecutedRows() {
        Map<String, Object> mvp =
                Map.of(
                        "operational",
                        Map.of("outcome", BenchmarkItemOutcome.EXECUTED.name()),
                        "generation",
                        Map.of("normalizedExactMatch", true),
                        "analysis",
                        Map.of(
                                RagPresetToolMetrics.KEY_TOOL_APPLICABLE,
                                true,
                                RagPresetToolMetrics.KEY_TOOL_EXECUTED,
                                true,
                                RagPresetToolMetrics.KEY_TOOL_SUCCEEDED,
                                true,
                                RagPresetToolMetrics.KEY_DETERMINISTIC_TOOL_ROUTE,
                                true,
                                RagPresetToolMetrics.KEY_TOOL_RESULT_USED_AS_FINAL,
                                true,
                                RagPresetToolMetrics.KEY_TOOL_COVERAGE_STATUS,
                                ToolCoverageStatus.APPLICABLE.name(),
                                "structuredScore",
                                1.0));

        Map<String, Object> rollup = BenchmarkMvpRollupCalculator.build(List.of(), null);
        rollup = BenchmarkMvpRollupCalculator.build(List.of(), null);
        Map<String, Object> bucket = BenchmarkMvpRollupCalculator.rollupBucket(List.of(mvp));
        @SuppressWarnings("unchecked")
        Map<String, Object> onExecuted = (Map<String, Object>) bucket.get("onExecuted");
        assertThat(onExecuted.get("toolCoverageRate")).isEqualTo(1.0);
        assertThat(onExecuted.get("toolExecutionRate")).isEqualTo(1.0);
        assertThat(onExecuted.get("toolSuccessRate")).isEqualTo(1.0);
        assertThat(onExecuted.get("toolFinalAnswerRate")).isEqualTo(1.0);
        assertThat(onExecuted.get("meanStructuredScoreToolApplicable")).isEqualTo(1.0);
    }
}

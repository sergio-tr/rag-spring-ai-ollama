package com.uniovi.rag.application.service.evaluation.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.application.service.evaluation.StartBenchmarkRunRequest;
import com.uniovi.rag.domain.evaluation.EvaluationRunKind;
import com.uniovi.rag.domain.evaluation.workbook.LlmRoleEvalCase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RoleEvalCaseSubsetSupportTest {

    @Test
    void isRoleEvalMode_whenRuntimeFlagSet() {
        StartBenchmarkRunRequest req = request(List.of(), Map.of(RoleEvalCaseSubsetSupport.RUNTIME_ROLE_EVAL_MODE, true));
        assertThat(RoleEvalCaseSubsetSupport.isRoleEvalMode(req)).isTrue();
    }

    @Test
    void isRoleEvalMode_whenCaseIdsLookLikeRoleCases() {
        StartBenchmarkRunRequest req = request(List.of("LLM-RW-001", "LLM-JS-002"), Map.of());
        assertThat(RoleEvalCaseSubsetSupport.isRoleEvalMode(req)).isTrue();
    }

    @Test
    void filter_bySubsetAndRoleProfile() {
        LlmRoleEvalCase rw =
                new LlmRoleEvalCase(
                        "LLM-RW-001",
                        "LLM_REWRITE_EXPANSION",
                        "QUERY_REWRITE",
                        "REWRITE_BALANCED",
                        "in",
                        "",
                        "out",
                        "kw",
                        "",
                        "normalized_match",
                        "",
                        "");
        LlmRoleEvalCase js =
                new LlmRoleEvalCase(
                        "LLM-JS-001",
                        "LLM_JSON_REASONING",
                        "METADATA_REASONING",
                        "JSON_STRICT",
                        "in",
                        "ctx",
                        "{}",
                        "",
                        "",
                        "json_schema",
                        "key",
                        "");
        RoleEvalCaseSubsetSupport.RoleEvalFilter filter =
                new RoleEvalCaseSubsetSupport.RoleEvalFilter(
                        "LLM_JSON_REASONING", null, "JSON_STRICT", List.of());
        List<LlmRoleEvalCase> out =
                RoleEvalCaseSubsetSupport.filter(List.of(rw, js), filter);
        assertThat(out).containsExactly(js);
    }

    @Test
    void filter_byExplicitCaseIds() {
        LlmRoleEvalCase a = caseRow("LLM-RW-001");
        LlmRoleEvalCase b = caseRow("LLM-RW-002");
        RoleEvalCaseSubsetSupport.RoleEvalFilter filter =
                new RoleEvalCaseSubsetSupport.RoleEvalFilter(null, null, null, List.of("LLM-RW-002"));
        assertThat(RoleEvalCaseSubsetSupport.filter(List.of(a, b), filter)).containsExactly(b);
    }

    private static StartBenchmarkRunRequest request(
            List<String> datasetQuestionIds, Map<String, Object> benchmarkRuntimeParameters) {
        return new StartBenchmarkRunRequest(
                null,
                null,
                null,
                EvaluationRunKind.PRODUCT_EXPLORATION,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                null,
                List.of(),
                List.of(),
                false,
                null,
                false,
                false,
                true,
                true,
                false,
                null,
                null,
                true,
                true,
                List.of(),
                datasetQuestionIds,
                null,
                null,
                benchmarkRuntimeParameters);
    }

    private static LlmRoleEvalCase caseRow(String id) {
        return new LlmRoleEvalCase(
                id, "LLM_REWRITE_EXPANSION", "QUERY_REWRITE", "REWRITE_BALANCED", "in", "", "out", "", "", "normalized_match", "", "");
    }
}

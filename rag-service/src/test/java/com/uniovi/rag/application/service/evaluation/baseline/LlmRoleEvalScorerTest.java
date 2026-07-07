package com.uniovi.rag.application.service.evaluation.baseline;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.evaluation.workbook.LlmRoleEvalCase;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LlmRoleEvalScorerTest {

    @Test
    void jsonSchema_passesWhenRequiredKeysPresent() {
        LlmRoleEvalCase c =
                new LlmRoleEvalCase(
                        "LLM-JS-001",
                        "LLM_JSON_REASONING",
                        "METADATA_REASONING",
                        "JSON_STRICT",
                        "task",
                        "",
                        "{\"answer\":true}",
                        "",
                        "",
                        "json_schema",
                        "answer,confidence",
                        "");
        Map<String, Object> scored = LlmRoleEvalScorer.score(c, "{\"answer\":true,\"confidence\":0.9}", null);
        assertThat(scored.get("roleEvalPassed")).isEqualTo(true);
    }

    @Test
    void normalizedMatch_failsOnMismatch() {
        LlmRoleEvalCase c =
                new LlmRoleEvalCase(
                        "LLM-RW-001",
                        "LLM_REWRITE_EXPANSION",
                        "QUERY_REWRITE",
                        "REWRITE_BALANCED",
                        "in",
                        "",
                        "expected answer",
                        "",
                        "",
                        "normalized_match",
                        "",
                        "");
        Map<String, Object> scored = LlmRoleEvalScorer.score(c, "different", null);
        assertThat(scored.get("roleEvalPassed")).isEqualTo(false);
    }

    @Test
    void keywordCoverage_countsHits() {
        LlmRoleEvalCase c =
                new LlmRoleEvalCase(
                        "LLM-RW-002",
                        "LLM_REWRITE_EXPANSION",
                        "QUERY_REWRITE",
                        "REWRITE_BALANCED",
                        "in",
                        "",
                        "out",
                        "alpha,beta,gamma",
                        "forbidden",
                        "keyword_coverage",
                        "",
                        "");
        Map<String, Object> scored = LlmRoleEvalScorer.score(c, "has alpha and beta", null);
        assertThat(scored.get("roleEvalPassed")).isEqualTo(false);
        @SuppressWarnings("unchecked")
        Map<String, Object> kw = (Map<String, Object>) ((Map<?, ?>) scored.get("scores")).get("keyword_coverage");
        assertThat(kw.get("metric")).isEqualTo(2.0 / 3.0);
    }
}

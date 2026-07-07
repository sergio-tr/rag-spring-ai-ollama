package com.uniovi.rag.application.service.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.uniovi.rag.domain.evaluation.snapshot.ExperimentalSnapshotFieldSource;
import com.uniovi.rag.domain.evaluation.snapshot.LlmExperimentalSnapshot;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BenchmarkRuntimeParametersSupportTest {

  @Test
  void applyToLlmSnapshot_overridesGenerationScalarsAndAdditionalParameters() {
    LlmExperimentalSnapshot base =
        new LlmExperimentalSnapshot(
            "gpt-oss:20b",
            0.7,
            null,
            10,
            null,
            null,
            8192,
            null,
            null,
            null,
            List.of(),
            null,
            false,
            "OPENAI_COMPATIBLE",
            "OPENAI_COMPATIBLE",
            5000,
            Map.of(),
            Map.of(),
            List.of());

    Map<String, Object> runtime =
        Map.of(
            "temperature", 0.1,
            "top_p", 0.85,
            "max_tokens", 768,
            "seed", 42,
            "presence_penalty", -0.2,
            "frequency_penalty", 0.3,
            "think", true,
            "response_format", Map.of("type", "json_object"),
            "stop", List.of("END"));

    LlmExperimentalSnapshot out = BenchmarkRuntimeParametersSupport.applyToLlmSnapshot(base, runtime);

    assertThat(out.temperature()).isEqualTo(0.1);
    assertThat(out.topP()).isEqualTo(0.85);
    assertThat(out.maxTokens()).isEqualTo(768);
    assertThat(out.seed()).isEqualTo(42);
    assertThat(out.stopSequences()).containsExactly("END");
    assertThat(out.additionalParameters()).containsEntry("think", true);
    assertThat(out.additionalParameters()).containsEntry("presence_penalty", -0.2);
    assertThat(out.additionalParameters()).containsEntry("frequency_penalty", 0.3);
    assertThat(out.fieldSources().get("temperature")).isEqualTo(ExperimentalSnapshotFieldSource.RUN_OVERRIDE.name());
  }

  @Test
  void mergeRetrievalOverrides_appliesTopKAndSimilarityThreshold() {
    var terminal =
        BenchmarkRuntimeParametersSupport.mergeRetrievalOverrides(
                JsonNodeFactory.instance.objectNode(),
                Map.of("topK", 12, "similarityThreshold", 0.66));

    assertThat(terminal.get("topK").asInt()).isEqualTo(12);
    assertThat(terminal.get("similarityThreshold").asDouble()).isEqualTo(0.66);
  }

  @Test
  void readFromRun_readsBenchmarkRuntimeParametersAggregate() {
    EvaluationRunEntity run = new EvaluationRunEntity();
    Map<String, Object> agg = new LinkedHashMap<>();
    agg.put(BenchmarkRunOrchestrator.AGG_KEY_BENCHMARK_RUNTIME_PARAMETERS, Map.of("temperature", 0.2));
    run.setAggregatesJson(agg);

    assertThat(BenchmarkRuntimeParametersSupport.readFromRun(run)).containsEntry("temperature", 0.2);
  }

  @Test
  void hasGenerationParameters_detectsGenerationKeysOnly() {
    assertThat(BenchmarkRuntimeParametersSupport.hasGenerationParameters(Map.of("topK", 5))).isFalse();
    assertThat(BenchmarkRuntimeParametersSupport.hasGenerationParameters(Map.of("temperature", 0.1))).isTrue();
  }
}

package com.uniovi.rag.application.service.evaluation;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uniovi.rag.application.service.embedding.EmbeddingBenchmarkRuntimeParameters;
import com.uniovi.rag.domain.evaluation.snapshot.ExperimentalSnapshotFieldSource;
import com.uniovi.rag.domain.evaluation.snapshot.LlmExperimentalSnapshot;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Applies Lab {@code benchmarkRuntimeParameters} from run aggregates onto snapshots and runtime JSON. */
public final class BenchmarkRuntimeParametersSupport {

    private static final String SOURCE = ExperimentalSnapshotFieldSource.RUN_OVERRIDE.name();

    private BenchmarkRuntimeParametersSupport() {}

    public static Map<String, Object> readFromRun(EvaluationRunEntity run) {
        if (run == null || run.getAggregatesJson() == null || run.getAggregatesJson().isEmpty()) {
            return Map.of();
        }
        Object raw = run.getAggregatesJson().get(BenchmarkRunOrchestrator.AGG_KEY_BENCHMARK_RUNTIME_PARAMETERS);
        return toMap(raw);
    }

    public static LlmExperimentalSnapshot applyToLlmSnapshot(
            LlmExperimentalSnapshot base, Map<String, Object> runtimeParameters) {
        Objects.requireNonNull(base, "base");
        if (runtimeParameters == null || runtimeParameters.isEmpty()) {
            return base;
        }
        Map<String, Object> params = Map.copyOf(runtimeParameters);
        Map<String, Object> additional = new LinkedHashMap<>(base.additionalParameters());
        Map<String, String> fieldSources = new LinkedHashMap<>(base.fieldSources());

        Double temperature = firstDouble(params, "temperature");
        Double topP = firstDouble(params, "top_p", "topP");
        Integer maxTokens = firstInteger(params, "max_tokens", "maxTokens");
        Integer seed = firstInteger(params, "seed");
        Double presencePenalty = firstDouble(params, "presence_penalty", "presencePenalty");
        Double frequencyPenalty = firstDouble(params, "frequency_penalty", "frequencyPenalty");
        Boolean think = firstBoolean(params, "think");
        Object responseFormat = readResponseFormat(params.get("response_format"), params.get("responseFormat"));
        List<String> stop = readStopList(params.get("stop"));

        if (temperature != null) {
            fieldSources.put("temperature", SOURCE);
        }
        if (topP != null) {
            fieldSources.put("topP", SOURCE);
        }
        if (maxTokens != null) {
            fieldSources.put("maxTokens", SOURCE);
        }
        if (seed != null) {
            fieldSources.put("seed", SOURCE);
            additional.put("seed", seed);
        }
        if (presencePenalty != null) {
            additional.put("presence_penalty", presencePenalty);
            additional.put("presencePenalty", presencePenalty);
        }
        if (frequencyPenalty != null) {
            additional.put("frequency_penalty", frequencyPenalty);
            additional.put("frequencyPenalty", frequencyPenalty);
        }
        if (think != null) {
            additional.put("think", think);
        }
        if (responseFormat != null) {
            additional.put("response_format", responseFormat);
            additional.put("responseFormat", responseFormat);
            fieldSources.put("outputFormat", SOURCE);
        }
        if (!stop.isEmpty()) {
            fieldSources.put("stopSequences", SOURCE);
        }

        return new LlmExperimentalSnapshot(
                base.model(),
                temperature != null ? temperature : base.temperature(),
                topP != null ? topP : base.topP(),
                base.topK(),
                base.minP(),
                base.repeatPenalty(),
                base.numCtx(),
                maxTokens != null ? maxTokens : base.maxTokens(),
                base.numPredict(),
                seed != null ? seed : base.seed(),
                stop.isEmpty() ? base.stopSequences() : stop,
                responseFormat != null ? responseFormat : base.outputFormat(),
                base.streaming(),
                base.chatProvider(),
                base.embeddingProvider(),
                base.timeoutMs(),
                Map.copyOf(additional),
                Map.copyOf(fieldSources),
                base.unsupportedFields());
    }

    public static int overlayTopK(int baseTopK, Map<String, Object> runtimeParameters) {
        Integer override = firstInteger(runtimeParameters, "topK", "top_k");
        if (override == null && runtimeParameters != null) {
            override = EmbeddingBenchmarkRuntimeParameters.readRetrievalOptions(runtimeParameters).topK();
        }
        return override != null && override > 0 ? override : baseTopK;
    }

    public static double overlaySimilarityThreshold(double baseThreshold, Map<String, Object> runtimeParameters) {
        Double override = firstDouble(runtimeParameters, "similarityThreshold", "similarity_threshold");
        if (override == null && runtimeParameters != null) {
            override = EmbeddingBenchmarkRuntimeParameters.readRetrievalOptions(runtimeParameters).similarityThreshold();
        }
        return override != null ? override : baseThreshold;
    }

    public static ObjectNode mergeRetrievalOverrides(ObjectNode terminal, Map<String, Object> runtimeParameters) {
        ObjectNode out = terminal != null ? terminal.deepCopy() : JsonNodeFactory.instance.objectNode();
        if (runtimeParameters == null || runtimeParameters.isEmpty()) {
            return out;
        }
        Integer topK = firstInteger(runtimeParameters, "topK", "top_k");
        Double threshold = firstDouble(runtimeParameters, "similarityThreshold", "similarity_threshold");
        if (topK == null && runtimeParameters != null) {
            topK = EmbeddingBenchmarkRuntimeParameters.readRetrievalOptions(runtimeParameters).topK();
        }
        if (threshold == null && runtimeParameters != null) {
            threshold = EmbeddingBenchmarkRuntimeParameters.readRetrievalOptions(runtimeParameters).similarityThreshold();
        }
        if (topK != null && topK > 0) {
            out.put("topK", topK);
        }
        if (threshold != null) {
            out.put("similarityThreshold", threshold);
        }
        return out;
    }

    public static boolean hasGenerationParameters(Map<String, Object> runtimeParameters) {
        if (runtimeParameters == null || runtimeParameters.isEmpty()) {
            return false;
        }
        for (String key :
                List.of(
                        "temperature",
                        "top_p",
                        "topP",
                        "seed",
                        "max_tokens",
                        "maxTokens",
                        "presence_penalty",
                        "presencePenalty",
                        "frequency_penalty",
                        "frequencyPenalty",
                        "response_format",
                        "responseFormat",
                        "stop",
                        "think")) {
            if (runtimeParameters.containsKey(key) && runtimeParameters.get(key) != null) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Object> toMap(Object raw) {
        if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() instanceof String key && !key.isBlank() && entry.getValue() != null) {
                out.put(key.trim(), entry.getValue());
            }
        }
        return Map.copyOf(out);
    }

    private static Double firstDouble(Map<String, Object> params, String... keys) {
        for (String key : keys) {
            Object raw = params.get(key);
            if (raw instanceof Number number) {
                return number.doubleValue();
            }
        }
        return null;
    }

    private static Integer firstInteger(Map<String, Object> params, String... keys) {
        for (String key : keys) {
            Object raw = params.get(key);
            if (raw instanceof Number number) {
                return number.intValue();
            }
        }
        return null;
    }

    private static Boolean firstBoolean(Map<String, Object> params, String... keys) {
        for (String key : keys) {
            Object raw = params.get(key);
            if (raw instanceof Boolean bool) {
                return bool;
            }
        }
        return null;
    }

    private static Object readResponseFormat(Object snake, Object camel) {
        Object raw = snake != null ? snake : camel;
        if (raw instanceof Map<?, ?> map && !map.isEmpty()) {
            return Map.copyOf(map);
        }
        if (raw instanceof String text) {
            String normalized = text.trim();
            if ("json_object".equalsIgnoreCase(normalized)) {
                return Map.of("type", "json_object");
            }
        }
        return null;
    }

    private static List<String> readStopList(Object raw) {
        if (raw instanceof String stop && !stop.isBlank()) {
            return List.of(stop.trim());
        }
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof String s && !s.isBlank()) {
                out.add(s.trim());
            }
        }
        return List.copyOf(out);
    }
}

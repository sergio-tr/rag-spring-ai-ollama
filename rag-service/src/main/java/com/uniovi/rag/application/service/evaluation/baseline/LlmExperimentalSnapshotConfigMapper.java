package com.uniovi.rag.application.service.evaluation.baseline;

import com.uniovi.rag.domain.evaluation.snapshot.LlmExperimentalSnapshot;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Maps a persisted {@link LlmExperimentalSnapshot} onto a resolved config shell for provider-aware Lab calls. */
final class LlmExperimentalSnapshotConfigMapper {

    private LlmExperimentalSnapshotConfigMapper() {}

    static ResolvedLlmConfig toResolvedConfig(ResolvedLlmConfig base, LlmExperimentalSnapshot snapshot) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(snapshot, "snapshot");
        LlmProvider chatProvider = parseProvider(snapshot.chatProvider(), base.chatProvider());
        LlmProvider embeddingProvider = parseProvider(snapshot.embeddingProvider(), base.embeddingProvider());
        String model = snapshot.model() != null && !snapshot.model().isBlank() ? snapshot.model() : base.chatModel();
        Map<String, Object> params = mergeParameters(base.additionalParameters(), snapshot);
        return new ResolvedLlmConfig(
                chatProvider,
                embeddingProvider,
                base.baseUrl(),
                model,
                base.embeddingModel(),
                base.apiKeyEnv(),
                base.secretName(),
                snapshot.temperature() != null ? snapshot.temperature() : base.temperature(),
                snapshot.timeoutMs() != null ? snapshot.timeoutMs() : base.timeoutMs(),
                base.systemPrompt(),
                params);
    }

    private static LlmProvider parseProvider(String raw, LlmProvider fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return LlmProvider.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static Map<String, Object> mergeParameters(
            Map<String, Object> baseParams, LlmExperimentalSnapshot snapshot) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (baseParams != null) {
            merged.putAll(baseParams);
        }
        if (snapshot.additionalParameters() != null) {
            merged.putAll(snapshot.additionalParameters());
        }
        putIfPresent(merged, "topP", snapshot.topP());
        putIfPresent(merged, "topK", snapshot.topK());
        putIfPresent(merged, "minP", snapshot.minP());
        putIfPresent(merged, "repeatPenalty", snapshot.repeatPenalty());
        putIfPresent(merged, "numCtx", snapshot.numCtx());
        putIfPresent(merged, "maxTokens", snapshot.maxTokens());
        putIfPresent(merged, "numPredict", snapshot.numPredict());
        putIfPresent(merged, "seed", snapshot.seed());
        putIfPresent(merged, "presencePenalty", readDouble(merged, "presencePenalty", "presence_penalty"));
        putIfPresent(merged, "frequencyPenalty", readDouble(merged, "frequencyPenalty", "frequency_penalty"));
        putIfPresent(merged, "think", readBoolean(merged, "think"));
        if (snapshot.outputFormat() instanceof Map<?, ?> formatMap && !formatMap.isEmpty()) {
            merged.put("response_format", Map.copyOf(formatMap));
            merged.put("responseFormat", Map.copyOf(formatMap));
        }
        if (snapshot.stopSequences() != null && !snapshot.stopSequences().isEmpty()) {
            merged.put("stop", snapshot.stopSequences());
        }
        if (snapshot.outputFormat() != null) {
            merged.put("format", snapshot.outputFormat());
        }
        return Map.copyOf(merged);
    }

    private static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private static Double readDouble(Map<String, Object> parameters, String... keys) {
        if (parameters == null || parameters.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            if (!parameters.containsKey(key)) {
                continue;
            }
            Object raw = parameters.get(key);
            if (raw instanceof Number number) {
                return number.doubleValue();
            }
        }
        return null;
    }

    private static Boolean readBoolean(Map<String, Object> parameters, String... keys) {
        if (parameters == null || parameters.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            if (!parameters.containsKey(key)) {
                continue;
            }
            Object raw = parameters.get(key);
            if (raw instanceof Boolean bool) {
                return bool;
            }
        }
        return null;
    }
}

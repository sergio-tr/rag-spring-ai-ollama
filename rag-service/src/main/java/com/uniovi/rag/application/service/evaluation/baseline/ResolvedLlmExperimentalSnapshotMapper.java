package com.uniovi.rag.application.service.evaluation.baseline;

import com.uniovi.rag.domain.evaluation.snapshot.ExperimentalSnapshotFieldSource;
import com.uniovi.rag.domain.evaluation.snapshot.LlmExperimentalSnapshot;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Maps {@link ResolvedLlmConfig} into {@link LlmExperimentalSnapshot} fields for Lab reproducibility. */
final class ResolvedLlmExperimentalSnapshotMapper {

    private ResolvedLlmExperimentalSnapshotMapper() {}

    static LlmExperimentalSnapshot toLlmSnapshot(
            ResolvedLlmConfig config,
            String effectiveModelId,
            ExperimentalSnapshotFieldSource modelSource,
            int applicationDefaultTopK,
            int applicationDefaultNumCtx) {
        Map<String, String> fieldSources = new LinkedHashMap<>();
        Map<String, Object> params = config != null ? config.additionalParameters() : Map.of();

        String chatProvider = config != null ? config.chatProvider().name() : null;
        String embeddingProvider = config != null ? config.embeddingProvider().name() : null;
        fieldSources.put("chatProvider", sourceOrUnknown(config != null));
        fieldSources.put("embeddingProvider", sourceOrUnknown(config != null));
        fieldSources.put("model", modelSource.name());

        Double temperature = config != null ? config.temperature() : null;
        fieldSources.put("temperature", temperature != null ? ExperimentalSnapshotFieldSource.RESOLVED_CONFIG.name() : ExperimentalSnapshotFieldSource.UNKNOWN.name());

        Integer timeoutMs = config != null ? config.timeoutMs() : null;
        fieldSources.put(
                "timeoutMs",
                timeoutMs != null
                        ? ExperimentalSnapshotFieldSource.RESOLVED_CONFIG.name()
                        : ExperimentalSnapshotFieldSource.NOT_APPLIED.name());

        Double topP = readDouble(params, "topP", "top_p");
        fieldSources.put("topP", paramSource(topP));

        Integer topK = readInteger(params, "topK", "top_k");
        if (topK == null) {
            topK = applicationDefaultTopK;
            fieldSources.put("topK", ExperimentalSnapshotFieldSource.APPLICATION_DEFAULT.name());
        } else {
            fieldSources.put("topK", ExperimentalSnapshotFieldSource.RESOLVED_CONFIG.name());
        }

        Double repeatPenalty = readDouble(params, "repeatPenalty", "repeat_penalty");
        fieldSources.put("repeatPenalty", paramSource(repeatPenalty));

        Integer numCtx = readInteger(params, "numCtx", "num_ctx");
        if (numCtx == null) {
            numCtx = applicationDefaultNumCtx;
            fieldSources.put("numCtx", ExperimentalSnapshotFieldSource.APPLICATION_DEFAULT.name());
        } else {
            fieldSources.put("numCtx", ExperimentalSnapshotFieldSource.RESOLVED_CONFIG.name());
        }

        Integer maxTokens = readInteger(params, "maxTokens", "max_tokens");
        fieldSources.put("maxTokens", paramSource(maxTokens));

        Integer numPredict = readInteger(params, "numPredict", "num_predict");
        fieldSources.put("numPredict", paramSource(numPredict));

        Integer seed = readInteger(params, "seed");
        fieldSources.put("seed", paramSource(seed));

        Double minP = readDouble(params, "minP", "min_p");
        fieldSources.put("minP", paramSource(minP));

        List<String> stopSequences = readStringList(params.get("stop"));
        fieldSources.put(
                "stopSequences",
                stopSequences.isEmpty()
                        ? ExperimentalSnapshotFieldSource.UNKNOWN.name()
                        : ExperimentalSnapshotFieldSource.RESOLVED_CONFIG.name());

        Object outputFormat = params.get("format");
        fieldSources.put("outputFormat", outputFormat != null ? ExperimentalSnapshotFieldSource.RESOLVED_CONFIG.name() : ExperimentalSnapshotFieldSource.UNKNOWN.name());

        Boolean streaming = readBoolean(params, "streaming");
        fieldSources.put(
                "streaming",
                streaming != null ? ExperimentalSnapshotFieldSource.RESOLVED_CONFIG.name() : ExperimentalSnapshotFieldSource.NOT_APPLIED.name());

        List<String> unsupported = unsupportedFields(config, params);
        return new LlmExperimentalSnapshot(
                effectiveModelId,
                temperature,
                topP,
                topK,
                minP,
                repeatPenalty,
                numCtx,
                maxTokens,
                numPredict,
                seed,
                stopSequences,
                outputFormat,
                streaming,
                chatProvider,
                embeddingProvider,
                timeoutMs,
                params,
                Map.copyOf(fieldSources),
                unsupported);
    }

    private static String sourceOrUnknown(boolean resolved) {
        return resolved ? ExperimentalSnapshotFieldSource.RESOLVED_CONFIG.name() : ExperimentalSnapshotFieldSource.UNKNOWN.name();
    }

    private static String paramSource(Number value) {
        return value != null
                ? ExperimentalSnapshotFieldSource.RESOLVED_CONFIG.name()
                : ExperimentalSnapshotFieldSource.UNKNOWN.name();
    }

    private static List<String> unsupportedFields(ResolvedLlmConfig config, Map<String, Object> params) {
        List<String> unsupported = new ArrayList<>();
        unsupported.add("minP");
        if (config != null && config.chatProvider() == LlmProvider.OPENAI_COMPATIBLE) {
            unsupported.add("topK");
            unsupported.add("repeatPenalty");
            unsupported.add("numCtx");
            unsupported.add("numPredict");
        }
        if (params.containsKey("minP") || params.containsKey("min_p")) {
            if (!unsupported.contains("minP")) {
                unsupported.add("minP");
            }
        }
        return List.copyOf(unsupported);
    }

    private static List<String> readStringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            if (item != null) {
                String s = String.valueOf(item).trim();
                if (!s.isEmpty()) {
                    out.add(s);
                }
            }
        }
        return List.copyOf(out);
    }

    private static Integer readInteger(Map<String, Object> parameters, String... keys) {
        for (String key : keys) {
            if (!parameters.containsKey(key)) {
                continue;
            }
            Object raw = parameters.get(key);
            if (raw instanceof Number number) {
                return number.intValue();
            }
        }
        return null;
    }

    private static Double readDouble(Map<String, Object> parameters, String... keys) {
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

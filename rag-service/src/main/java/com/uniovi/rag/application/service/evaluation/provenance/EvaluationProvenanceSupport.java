package com.uniovi.rag.application.service.evaluation.provenance;

import com.uniovi.rag.domain.evaluation.snapshot.PromptProfileSnapshot;
import com.uniovi.rag.infrastructure.config.PromptBundleFingerprint;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import java.util.LinkedHashMap;
import java.util.Map;

/** Builds and reads evaluation provenance for reproducible Lab exports. */
public final class EvaluationProvenanceSupport {

    private EvaluationProvenanceSupport() {}

    public static Map<String, Object> build(
            ResolvedLlmConfig config, PromptProfileSnapshot prompts, EvaluationBuildMetadata buildMetadata) {
        PromptBundleFingerprint.Result bundle =
                PromptBundleFingerprint.computeForEvaluation(
                        prompts, config != null ? config.systemPrompt() : null);
        return build(config, prompts, buildMetadata, bundle);
    }

    public static Map<String, Object> build(
            ResolvedLlmConfig config,
            PromptProfileSnapshot prompts,
            EvaluationBuildMetadata buildMetadata,
            PromptBundleFingerprint.Result promptBundle) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (config != null) {
            out.put(EvaluationProvenanceKeys.CHAT_PROVIDER, config.chatProvider().name());
            out.put(EvaluationProvenanceKeys.EMBEDDING_PROVIDER, config.embeddingProvider().name());
        }
        if (prompts != null) {
            if (prompts.profileVersion() != null && !prompts.profileVersion().isBlank()) {
                out.put(EvaluationProvenanceKeys.PROMPT_PROFILE_VERSION, prompts.profileVersion().trim());
            }
            if (prompts.effectiveSystemPromptSha256() != null
                    && !prompts.effectiveSystemPromptSha256().isBlank()) {
                out.put(
                        EvaluationProvenanceKeys.EFFECTIVE_SYSTEM_PROMPT_SHA256,
                        prompts.effectiveSystemPromptSha256().trim());
            }
        }
        if (promptBundle != null) {
            out.putAll(promptBundle.toProvenanceMap());
        }
        if (buildMetadata != null) {
            out.putAll(buildMetadata.asMap());
        }
        return Map.copyOf(out);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> readFromRun(EvaluationRunEntity run) {
        if (run == null || run.getAggregatesJson() == null) {
            return Map.of();
        }
        Object raw = run.getAggregatesJson().get(EvaluationProvenanceKeys.AGGREGATES_KEY);
        if (!(raw instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (e.getKey() != null) {
                out.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
        return Map.copyOf(out);
    }

    public static Map<String, Object> mergeIntoAggregates(
            Map<String, Object> existingAggregates, Map<String, Object> provenance) {
        Map<String, Object> agg =
                existingAggregates != null ? new LinkedHashMap<>(existingAggregates) : new LinkedHashMap<>();
        if (provenance != null && !provenance.isEmpty()) {
            agg.put(EvaluationProvenanceKeys.AGGREGATES_KEY, Map.copyOf(provenance));
        }
        return Map.copyOf(agg);
    }

    public static void enrichMetricsFromRun(Map<String, Object> metrics, EvaluationRunEntity run) {
        if (metrics == null || run == null) {
            return;
        }
        Map<String, Object> provenance = readFromRun(run);
        putIfAbsent(metrics, EvaluationProvenanceKeys.CHAT_PROVIDER, provenance.get(EvaluationProvenanceKeys.CHAT_PROVIDER));
        putIfAbsent(
                metrics,
                EvaluationProvenanceKeys.EMBEDDING_PROVIDER,
                provenance.get(EvaluationProvenanceKeys.EMBEDDING_PROVIDER));
    }

    public static Map<String, Object> providerMetricsFromConfig(com.uniovi.rag.domain.llm.ResolvedLlmConfig config) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (config == null) {
            return Map.of();
        }
        out.put(EvaluationProvenanceKeys.CHAT_PROVIDER, config.chatProvider().name());
        out.put(EvaluationProvenanceKeys.EMBEDDING_PROVIDER, config.embeddingProvider().name());
        return Map.copyOf(out);
    }

    public static Map<String, Object> mergeLabMetrics(
            Map<String, Object> base, Map<String, Object> overlay) {
        if (overlay == null || overlay.isEmpty()) {
            return base != null ? base : Map.of();
        }
        Map<String, Object> merged = base != null ? new LinkedHashMap<>(base) : new LinkedHashMap<>();
        merged.putAll(overlay);
        return Map.copyOf(merged);
    }

    public static String stringValue(Map<String, Object> map, String key) {
        if (map == null || key == null || !map.containsKey(key)) {
            return "";
        }
        Object v = map.get(key);
        return v == null ? "" : String.valueOf(v).trim();
    }

    /** Ensures export-facing provenance always includes git/build/environment with {@code unknown} fallback. */
    public static Map<String, Object> withExportDefaults(Map<String, Object> provenance) {
        Map<String, Object> out = new LinkedHashMap<>(provenance != null ? provenance : Map.of());
        putDefaultIfBlank(out, EvaluationProvenanceKeys.GIT_SHA);
        putDefaultIfBlank(out, EvaluationProvenanceKeys.BUILD_ID);
        putDefaultIfBlank(out, EvaluationProvenanceKeys.ENVIRONMENT_LABEL);
        return Map.copyOf(out);
    }

    private static void putDefaultIfBlank(Map<String, Object> target, String key) {
        if (stringValue(target, key).isBlank()) {
            target.put(key, EvaluationBuildMetadata.UNKNOWN);
        }
    }

    private static void putIfAbsent(Map<String, Object> target, String key, Object value) {
        if (value == null) {
            return;
        }
        String s = String.valueOf(value).trim();
        if (s.isEmpty()) {
            return;
        }
        target.putIfAbsent(key, s);
    }
}

package com.uniovi.rag.domain.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Materialized RAG configuration after the 4-level cascade (system, user, project, runtime).
 * Immutable snapshot used by the query pipeline.
 */
public record RagConfig(
        boolean expansionEnabled,
        boolean nerEnabled,
        boolean toolsEnabled,
        boolean metadataEnabled,
        boolean reasoningEnabled,
        boolean rankerEnabled,
        boolean postRetrievalEnabled,
        boolean functionCallingEnabled,
        boolean useRetrieval,
        boolean useAdvisor,
        /** P11: deterministic clarification loop; requires persistable conversation scope to store pending state. */
        boolean clarificationEnabled,
        /** P12: bounded conversational memory stage (runtime-owned, default off). */
        boolean memoryEnabled,
        /** P13: deterministic adaptive routing stage (runtime-owned, default off). */
        boolean adaptiveRoutingEnabled,
        /** P14: post-answer judge stage (runtime-owned, default off). */
        boolean judgeEnabled,
        int topK,
        double similarityThreshold,
        String llmModel,
        String embeddingModel,
        String classifierModelId,
        String reasoningStrategy,
        /**
         * When {@code true} and the request is project-scoped, prompt context is built by concatenating
         * {@code vector_store} chunks for that project (capped by {@link #naiveFullCorpusMaxChars()}) instead of similarity search.
         */
        boolean naiveFullCorpusInPromptEnabled,
        int naiveFullCorpusMaxChars,
        /** Max characters for extractive curated retrieval context (advanced pipeline). */
        int advancedRetrievalMaxContextChars,
        /**
         * Lab P0 routing: when {@link #naiveFullCorpusInPromptEnabled()} is true without retrieval, select the direct
         * corpus-grounded workflow instead of the naive full-corpus baseline workflow name/metrics path.
         */
        boolean corpusGroundedDirectWorkflow,
        MaterializationStrategy materializationStrategy
) {

    public static final int DEFAULT_NAIVE_FULL_CORPUS_MAX_CHARS = 24_000;
    public static final int DEFAULT_ADVANCED_RETRIEVAL_MAX_CONTEXT_CHARS = 24_000;

    private static final String JSON_MATERIALIZATION_STRATEGY = "materializationStrategy";

    /**
     * Convenience constructor for positional call sites (defaults {@code corpusGroundedDirectWorkflow=false}).
     */
    public RagConfig(
            boolean expansionEnabled,
            boolean nerEnabled,
            boolean toolsEnabled,
            boolean metadataEnabled,
            boolean reasoningEnabled,
            boolean rankerEnabled,
            boolean postRetrievalEnabled,
            boolean functionCallingEnabled,
            boolean useRetrieval,
            boolean useAdvisor,
            boolean clarificationEnabled,
            boolean memoryEnabled,
            boolean adaptiveRoutingEnabled,
            boolean judgeEnabled,
            int topK,
            double similarityThreshold,
            String llmModel,
            String embeddingModel,
            String classifierModelId,
            String reasoningStrategy,
            boolean naiveFullCorpusInPromptEnabled,
            int naiveFullCorpusMaxChars,
            int advancedRetrievalMaxContextChars,
            MaterializationStrategy materializationStrategy) {
        this(
                expansionEnabled,
                nerEnabled,
                toolsEnabled,
                metadataEnabled,
                reasoningEnabled,
                rankerEnabled,
                postRetrievalEnabled,
                functionCallingEnabled,
                useRetrieval,
                useAdvisor,
                clarificationEnabled,
                memoryEnabled,
                adaptiveRoutingEnabled,
                judgeEnabled,
                topK,
                similarityThreshold,
                llmModel,
                embeddingModel,
                classifierModelId,
                reasoningStrategy,
                naiveFullCorpusInPromptEnabled,
                naiveFullCorpusMaxChars,
                advancedRetrievalMaxContextChars,
                false,
                materializationStrategy);
    }

    /**
     * Backwards-compatible constructor for call sites that predate P13.
     * Defaults {@code adaptiveRoutingEnabled=false}.
     */
    public RagConfig(
            boolean expansionEnabled,
            boolean nerEnabled,
            boolean toolsEnabled,
            boolean metadataEnabled,
            boolean reasoningEnabled,
            boolean rankerEnabled,
            boolean postRetrievalEnabled,
            boolean functionCallingEnabled,
            boolean useRetrieval,
            boolean useAdvisor,
            boolean clarificationEnabled,
            boolean memoryEnabled,
            int topK,
            double similarityThreshold,
            String llmModel,
            String embeddingModel,
            String classifierModelId,
            String reasoningStrategy,
            boolean naiveFullCorpusInPromptEnabled,
            int naiveFullCorpusMaxChars,
            int advancedRetrievalMaxContextChars,
            MaterializationStrategy materializationStrategy
    ) {
        this(
                expansionEnabled,
                nerEnabled,
                toolsEnabled,
                metadataEnabled,
                reasoningEnabled,
                rankerEnabled,
                postRetrievalEnabled,
                functionCallingEnabled,
                useRetrieval,
                useAdvisor,
                clarificationEnabled,
                memoryEnabled,
                false,
                false,
                topK,
                similarityThreshold,
                llmModel,
                embeddingModel,
                classifierModelId,
                reasoningStrategy,
                naiveFullCorpusInPromptEnabled,
                naiveFullCorpusMaxChars,
                advancedRetrievalMaxContextChars,
                false,
                materializationStrategy);
    }

    /**
     * Backwards-compatible constructor for call sites that predate P14.
     * Defaults {@code judgeEnabled=false}.
     */
    public RagConfig(
            boolean expansionEnabled,
            boolean nerEnabled,
            boolean toolsEnabled,
            boolean metadataEnabled,
            boolean reasoningEnabled,
            boolean rankerEnabled,
            boolean postRetrievalEnabled,
            boolean functionCallingEnabled,
            boolean useRetrieval,
            boolean useAdvisor,
            boolean clarificationEnabled,
            boolean memoryEnabled,
            boolean adaptiveRoutingEnabled,
            int topK,
            double similarityThreshold,
            String llmModel,
            String embeddingModel,
            String classifierModelId,
            String reasoningStrategy,
            boolean naiveFullCorpusInPromptEnabled,
            int naiveFullCorpusMaxChars,
            int advancedRetrievalMaxContextChars,
            MaterializationStrategy materializationStrategy
    ) {
        this(
                expansionEnabled,
                nerEnabled,
                toolsEnabled,
                metadataEnabled,
                reasoningEnabled,
                rankerEnabled,
                postRetrievalEnabled,
                functionCallingEnabled,
                useRetrieval,
                useAdvisor,
                clarificationEnabled,
                memoryEnabled,
                adaptiveRoutingEnabled,
                false,
                topK,
                similarityThreshold,
                llmModel,
                embeddingModel,
                classifierModelId,
                reasoningStrategy,
                naiveFullCorpusInPromptEnabled,
                naiveFullCorpusMaxChars,
                advancedRetrievalMaxContextChars,
                false,
                materializationStrategy);
    }

    public static RagConfig fromFeatureConfiguration(
            RagFeatureConfiguration features,
            int topK,
            double similarityThreshold,
            String llmModel,
            String embeddingModel,
            String classifierModelId,
            String reasoningStrategy) {
        return new RagConfig(
                features.isExpansionEnabled(),
                features.isNerEnabled(),
                features.isToolsEnabled(),
                features.isMetadataEnabled(),
                features.isReasoningEnabled(),
                features.isRankerEnabled(),
                features.isPostRetrievalEnabled(),
                features.isFunctionCallingEnabled(),
                features.isUseRetrieval(),
                features.isUseAdvisor(),
                features.isClarificationEnabled(),
                features.isMemoryEnabled(),
                features.isAdaptiveRoutingEnabled(),
                features.isJudgeEnabled(),
                topK,
                similarityThreshold,
                llmModel,
                embeddingModel,
                classifierModelId,
                reasoningStrategy,
                false,
                DEFAULT_NAIVE_FULL_CORPUS_MAX_CHARS,
                DEFAULT_ADVANCED_RETRIEVAL_MAX_CONTEXT_CHARS,
                false,
                MaterializationStrategy.CHUNK_LEVEL
        );
    }

    /**
     * Applies JSON key overrides stored in {@code rag_configuration.values} or {@code default_system_configuration.values}.
     * Unknown keys are ignored.
     */
    public static RagConfig applyJsonOverrides(RagConfig base, JsonNode json) {
        if (json == null || json.isNull() || json.isEmpty()) {
            return base;
        }
        int maxChars = readInt(json, "naiveFullCorpusMaxChars", base.naiveFullCorpusMaxChars);
        maxChars = Math.clamp(maxChars, 1024, 500_000);
        int advMax = readInt(json, "advancedRetrievalMaxContextChars", base.advancedRetrievalMaxContextChars);
        advMax = Math.clamp(advMax, 1024, 500_000);
        return new RagConfig(
                readBool(json, "expansionEnabled", base.expansionEnabled),
                readBool(json, "nerEnabled", base.nerEnabled),
                readBool(json, "toolsEnabled", base.toolsEnabled),
                readBool(json, "metadataEnabled", base.metadataEnabled),
                readBool(json, "reasoningEnabled", base.reasoningEnabled),
                readBool(json, "rankerEnabled", base.rankerEnabled),
                readBool(json, "postRetrievalEnabled", base.postRetrievalEnabled),
                readBool(json, "functionCallingEnabled", base.functionCallingEnabled),
                readBool(json, "useRetrieval", base.useRetrieval),
                readBool(json, "useAdvisor", base.useAdvisor),
                readBool(json, "clarificationEnabled", base.clarificationEnabled),
                readBool(json, "memoryEnabled", base.memoryEnabled),
                readBool(json, "adaptiveRoutingEnabled", base.adaptiveRoutingEnabled),
                readBool(json, "judgeEnabled", base.judgeEnabled),
                readInt(json, "topK", base.topK),
                readDouble(json, "similarityThreshold", base.similarityThreshold),
                readText(json, "llmModel", base.llmModel),
                readText(json, "embeddingModel", base.embeddingModel),
                readText(json, "classifierModelId", base.classifierModelId),
                readText(json, "reasoningStrategy", base.reasoningStrategy),
                readBool(json, "naiveFullCorpusInPromptEnabled", base.naiveFullCorpusInPromptEnabled),
                maxChars,
                advMax,
                readBool(json, "corpusGroundedDirectWorkflow", base.corpusGroundedDirectWorkflow),
                readMaterializationStrategy(json, base.materializationStrategy)
        );
    }

    private static MaterializationStrategy readMaterializationStrategy(JsonNode json, MaterializationStrategy base) {
        if (json == null
                || !json.hasNonNull(JSON_MATERIALIZATION_STRATEGY)
                || !json.get(JSON_MATERIALIZATION_STRATEGY).isTextual()) {
            return base;
        }
        try {
            return MaterializationStrategy.valueOf(json.get(JSON_MATERIALIZATION_STRATEGY).asText().trim());
        } catch (IllegalArgumentException e) {
            return base;
        }
    }

    private static boolean readBool(JsonNode json, String field, boolean defaultValue) {
        return json.hasNonNull(field) && json.get(field).isBoolean() ? json.get(field).asBoolean() : defaultValue;
    }

    private static int readInt(JsonNode json, String field, int defaultValue) {
        return json.hasNonNull(field) && json.get(field).isNumber() ? json.get(field).asInt() : defaultValue;
    }

    private static double readDouble(JsonNode json, String field, double defaultValue) {
        return json.hasNonNull(field) && json.get(field).isNumber() ? json.get(field).asDouble() : defaultValue;
    }

    private static String readText(JsonNode json, String field, String defaultValue) {
        return json.hasNonNull(field) && json.get(field).isTextual() ? json.get(field).asText() : defaultValue;
    }

    /**
     * Snapshot as JSON-compatible map; keys match {@link #applyJsonOverrides(RagConfig, JsonNode)}.
     */
    public Map<String, Object> toValueMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("expansionEnabled", expansionEnabled);
        m.put("nerEnabled", nerEnabled);
        m.put("toolsEnabled", toolsEnabled);
        m.put("metadataEnabled", metadataEnabled);
        m.put("reasoningEnabled", reasoningEnabled);
        m.put("rankerEnabled", rankerEnabled);
        m.put("postRetrievalEnabled", postRetrievalEnabled);
        m.put("functionCallingEnabled", functionCallingEnabled);
        m.put("useRetrieval", useRetrieval);
        m.put("useAdvisor", useAdvisor);
        m.put("clarificationEnabled", clarificationEnabled);
        m.put("memoryEnabled", memoryEnabled);
        m.put("adaptiveRoutingEnabled", adaptiveRoutingEnabled);
        m.put("judgeEnabled", judgeEnabled);
        m.put("topK", topK);
        m.put("similarityThreshold", similarityThreshold);
        m.put("llmModel", llmModel);
        m.put("embeddingModel", embeddingModel);
        m.put("classifierModelId", classifierModelId);
        m.put("reasoningStrategy", reasoningStrategy);
        m.put("naiveFullCorpusInPromptEnabled", naiveFullCorpusInPromptEnabled);
        m.put("naiveFullCorpusMaxChars", naiveFullCorpusMaxChars);
        m.put("advancedRetrievalMaxContextChars", advancedRetrievalMaxContextChars);
        m.put("corpusGroundedDirectWorkflow", corpusGroundedDirectWorkflow);
        m.put(JSON_MATERIALIZATION_STRATEGY, materializationStrategy.name());
        return m;
    }
}

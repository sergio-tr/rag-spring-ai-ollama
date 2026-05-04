package com.uniovi.rag.service.evaluation.preset;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;

/**
 * Maps thesis workbook presets P0–P14 onto (1) {@link RagFeatureConfiguration} for document loading / metadata services
 * and (2) terminal runtime JSON so {@link com.uniovi.rag.domain.runtime.RagConfig} merges include {@code naiveFullCorpusInPromptEnabled}, {@code topK}, etc.
 */
public final class RagPresetExperimentalOverlay {

    public record Overlay(RagFeatureConfiguration features, ObjectNode terminalRuntimeJson) {}

    private RagPresetExperimentalOverlay() {}

    public static Overlay build(RagFeatureConfiguration base, RagExperimentalPresetCode preset) {
        RagFeatureConfiguration f = RagFeatureConfigurationCopier.copy(base);
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        switch (preset) {
            case P0 -> applyDirectLlm(f, json);
            case P1 -> applyNaiveFullCorpus(f, json);
            case P2 -> applyChunkRetrievalBaseline(f, json);
            case P3 -> applySemanticRetrievalBaseline(f, json);
            case P4 -> applyMetadataTools(f, json);
            case P5 -> applyQueryUnderstanding(f, json);
            case P6 -> applyStructuredRewrite(f, json);
            case P7 -> applyDeterministicTools(f, json);
            case P8 -> applyHybridRankPost(f, json);
            case P9 -> applyFunctionCalling(f, json);
            case P10 -> applyAdvisor(f, json);
            case P11, P12 -> applyPlaceholder(f, json);
            case P13 -> applyAdaptiveRouting(f, json);
            case P14 -> applyJudge(f, json);
        }
        return new Overlay(f, json);
    }

    private static void applyDirectLlm(RagFeatureConfiguration f, ObjectNode json) {
        allPipelineFlagsOff(f);
        json.put("useRetrieval", false);
        json.put("useAdvisor", false);
        json.put("naiveFullCorpusInPromptEnabled", false);
        json.put("topK", 5);
        json.put("similarityThreshold", 0.7);
    }

    /** Mirrors system preset Demo_NaiveFullCorpus (concatenated corpus cap, no semantic retrieval behaviour). */
    private static void applyNaiveFullCorpus(RagFeatureConfiguration f, ObjectNode json) {
        allPipelineFlagsOff(f);
        f.setUseRetrieval(true);
        f.setUseAdvisor(false);
        json.put("expansionEnabled", false);
        json.put("nerEnabled", false);
        json.put("toolsEnabled", false);
        json.put("metadataEnabled", false);
        json.put("reasoningEnabled", false);
        json.put("rankerEnabled", false);
        json.put("postRetrievalEnabled", false);
        json.put("functionCallingEnabled", false);
        json.put("useRetrieval", true);
        json.put("useAdvisor", false);
        json.put("naiveFullCorpusInPromptEnabled", true);
        json.put("naiveFullCorpusMaxChars", 32_000);
        json.put("topK", 3);
        json.put("similarityThreshold", 0.9);
    }

    private static void applyChunkRetrievalBaseline(RagFeatureConfiguration f, ObjectNode json) {
        minimalRetrieval(f, json);
        json.put("topK", 8);
        json.put("similarityThreshold", 0.72);
    }

    private static void applySemanticRetrievalBaseline(RagFeatureConfiguration f, ObjectNode json) {
        minimalRetrieval(f, json);
        json.put("topK", 10);
        json.put("similarityThreshold", 0.7);
    }

    private static void applyMetadataTools(RagFeatureConfiguration f, ObjectNode json) {
        minimalRetrieval(f, json);
        f.setMetadataEnabled(true);
        f.setToolsEnabled(true);
        json.put("metadataEnabled", true);
        json.put("toolsEnabled", true);
        json.put("functionCallingEnabled", false);
        json.put("topK", 10);
    }

    private static void applyQueryUnderstanding(RagFeatureConfiguration f, ObjectNode json) {
        minimalRetrieval(f, json);
        f.setExpansionEnabled(true);
        f.setNerEnabled(true);
        json.put("expansionEnabled", true);
        json.put("nerEnabled", true);
        json.put("topK", 10);
    }

    private static void applyStructuredRewrite(RagFeatureConfiguration f, ObjectNode json) {
        minimalRetrieval(f, json);
        f.setReasoningEnabled(true);
        json.put("reasoningEnabled", true);
        json.put("reasoningStrategy", "COT");
        json.put("topK", 10);
    }

    private static void applyDeterministicTools(RagFeatureConfiguration f, ObjectNode json) {
        minimalRetrieval(f, json);
        f.setToolsEnabled(true);
        f.setFunctionCallingEnabled(false);
        json.put("toolsEnabled", true);
        json.put("functionCallingEnabled", false);
        json.put("topK", 10);
    }

    private static void applyHybridRankPost(RagFeatureConfiguration f, ObjectNode json) {
        minimalRetrieval(f, json);
        f.setRankerEnabled(true);
        f.setPostRetrievalEnabled(true);
        json.put("rankerEnabled", true);
        json.put("postRetrievalEnabled", true);
        json.put("topK", 12);
        json.put("similarityThreshold", 0.6);
    }

    private static void applyFunctionCalling(RagFeatureConfiguration f, ObjectNode json) {
        minimalRetrieval(f, json);
        f.setFunctionCallingEnabled(true);
        f.setToolsEnabled(false);
        json.put("functionCallingEnabled", true);
        json.put("toolsEnabled", false);
        json.put("topK", 12);
    }

    private static void applyAdvisor(RagFeatureConfiguration f, ObjectNode json) {
        minimalRetrieval(f, json);
        f.setUseAdvisor(true);
        json.put("useAdvisor", true);
        json.put("topK", 12);
    }

    /** Placeholder flags only — benchmark harness gates P11/P12 as NOT_SUPPORTED. */
    private static void applyPlaceholder(RagFeatureConfiguration f, ObjectNode json) {
        minimalRetrieval(f, json);
        f.setClarificationEnabled(true);
        f.setMemoryEnabled(true);
        json.put("clarificationEnabled", true);
        json.put("memoryEnabled", true);
    }

    private static void applyAdaptiveRouting(RagFeatureConfiguration f, ObjectNode json) {
        minimalRetrieval(f, json);
        f.setAdaptiveRoutingEnabled(true);
        json.put("adaptiveRoutingEnabled", true);
        json.put("topK", 12);
    }

    private static void applyJudge(RagFeatureConfiguration f, ObjectNode json) {
        minimalRetrieval(f, json);
        f.setJudgeEnabled(true);
        json.put("judgeEnabled", true);
        json.put("topK", 12);
    }

    private static void minimalRetrieval(RagFeatureConfiguration f, ObjectNode json) {
        allPipelineFlagsOff(f);
        f.setUseRetrieval(true);
        f.setUseAdvisor(false);
        json.put("useRetrieval", true);
        json.put("useAdvisor", false);
        json.put("naiveFullCorpusInPromptEnabled", false);
    }

    private static void allPipelineFlagsOff(RagFeatureConfiguration f) {
        f.setExpansionEnabled(false);
        f.setNerEnabled(false);
        f.setToolsEnabled(false);
        f.setMetadataEnabled(false);
        f.setReasoningEnabled(false);
        f.setRankerEnabled(false);
        f.setPostRetrievalEnabled(false);
        f.setFunctionCallingEnabled(false);
        f.setUseRetrieval(false);
        f.setUseAdvisor(false);
        f.setClarificationEnabled(false);
        f.setMemoryEnabled(false);
        f.setAdaptiveRoutingEnabled(false);
        f.setJudgeEnabled(false);
    }
}

package com.uniovi.rag.application.service.config;

import com.uniovi.rag.application.service.evaluation.preset.ExperimentalPresetCanonicalCatalog;
import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Product-aligned runtime defaults for Chat-visible presets (Phase 2.6).
 *
 * <p>Lab benchmarks and workbook annexes continue to use {@link ExperimentalPresetCanonicalCatalog}
 * unchanged. Persisted {@code rag_preset.values} for Chat execution are aligned via
 * {@code V78__chat_product_preset_defaults_alignment.sql} to match this layer.
 */
public final class ChatProductPresetAlignment {

    public static final UUID DEMO_WORST_PRESET_ID =
            UUID.fromString("cafe0001-0001-4001-8001-000000000001");
    public static final UUID DEMO_NAIVE_FULL_CORPUS_PRESET_ID =
            UUID.fromString("cafe0001-0001-4001-8001-000000000002");
    public static final UUID DEMO_BEST_PRESET_ID = ChatPresetDefaults.DETERMINISTIC_DEFAULT_CHAT_PRESET_ID;

    /** Product default similarity threshold for chat-visible presets (assistant/project layer also uses 0.1). */
    public static final double PRODUCT_SIMILARITY_THRESHOLD = 0.1;

    public static final String DEMO_BEST_PRODUCT_DESCRIPTION =
            "Production assistant: hybrid retrieval, metadata context, query intelligence, deterministic tools, "
                    + "function calling, advisor, and clarification. Ranker, reasoning, judge, and memory off for "
                    + "interactive latency.";

    private ChatProductPresetAlignment() {}

    /**
     * Effective product runtime map for experimental presets P0–P15.
     * Starts from the Lab catalog ladder and applies product-only corrections.
     */
    public static Map<String, Object> effectiveProductRuntimeValues(RagExperimentalPresetCode code) {
        LinkedHashMap<String, Object> out =
                new LinkedHashMap<>(ExperimentalPresetCanonicalCatalog.effectiveRuntimeValues(code));
        applyExperimentalProductCorrections(code, out);
        return Map.copyOf(out);
    }

    /** Product-aligned Demo_Best values (HYBRID+metadata production default). */
    public static Map<String, Object> demoBestProductValues() {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        out.put("useRetrieval", true);
        out.put("useAdvisor", true);
        out.put("naiveFullCorpusInPromptEnabled", false);
        out.put("corpusGroundedDirectWorkflow", false);
        out.put("materializationStrategy", "HYBRID");
        out.put("metadataEnabled", true);
        out.put("expansionEnabled", true);
        out.put("nerEnabled", true);
        out.put("toolsEnabled", true);
        out.put("deterministicToolRoutingEnabled", true);
        out.put("functionCallingEnabled", true);
        out.put("functionCallingBackendProposalEnabled", true);
        out.put("functionCallingNativeProviderEnabled", false);
        out.put("postRetrievalEnabled", true);
        out.put("clarificationEnabled", true);
        out.put("reasoningEnabled", false);
        out.put("rankerEnabled", false);
        out.put("judgeEnabled", false);
        out.put("memoryEnabled", false);
        out.put("adaptiveRoutingEnabled", false);
        out.put("topK", 12);
        out.put("similarityThreshold", PRODUCT_SIMILARITY_THRESHOLD);
        out.put("reasoningStrategy", "SIMPLE");
        out.put("naiveFullCorpusMaxChars", 24_000);
        return Map.copyOf(out);
    }

    /** Product-aligned Demo_Worst (direct LLM baseline). */
    public static Map<String, Object> demoWorstProductValues() {
        LinkedHashMap<String, Object> out =
                new LinkedHashMap<>(effectiveProductRuntimeValues(RagExperimentalPresetCode.P0));
        out.put("similarityThreshold", PRODUCT_SIMILARITY_THRESHOLD);
        out.put("naiveFullCorpusMaxChars", 24_000);
        out.put("reasoningStrategy", "SIMPLE");
        return Map.copyOf(out);
    }

    /** Product-aligned Demo_NaiveFullCorpus (full-context baseline; high threshold retained). */
    public static Map<String, Object> demoNaiveFullCorpusProductValues() {
        LinkedHashMap<String, Object> out =
                new LinkedHashMap<>(effectiveProductRuntimeValues(RagExperimentalPresetCode.P1));
        out.put("useRetrieval", false);
        out.put("naiveFullCorpusInPromptEnabled", true);
        out.put("naiveFullCorpusMaxChars", 32_000);
        out.put("similarityThreshold", 0.9);
        out.put("reasoningStrategy", "SIMPLE");
        return Map.copyOf(out);
    }

    public static boolean toolsEnabledByDefault(RagExperimentalPresetCode code) {
        Object v = effectiveProductRuntimeValues(code).get("toolsEnabled");
        return v instanceof Boolean b && b;
    }

    public static boolean reasoningEnabledByDefault(RagExperimentalPresetCode code) {
        Object v = effectiveProductRuntimeValues(code).get("reasoningEnabled");
        return v instanceof Boolean b && b;
    }

    private static void applyExperimentalProductCorrections(
            RagExperimentalPresetCode code, LinkedHashMap<String, Object> out) {
        if (code == RagExperimentalPresetCode.P5) {
            out.put("expansionEnabled", true);
            out.put("nerEnabled", false);
        }
        if (code == RagExperimentalPresetCode.P6) {
            out.put("expansionEnabled", true);
            out.put("nerEnabled", true);
        }

        if (code.ordinal() >= RagExperimentalPresetCode.P4.ordinal()
                && code.ordinal() <= RagExperimentalPresetCode.P6.ordinal()) {
            out.put("toolsEnabled", false);
        }

        if (code.ordinal() < RagExperimentalPresetCode.P10.ordinal()
                || code == RagExperimentalPresetCode.P15) {
            out.put("reasoningEnabled", false);
        } else {
            out.put("reasoningEnabled", true);
        }

        if (code != RagExperimentalPresetCode.P0 && code != RagExperimentalPresetCode.P1) {
            out.put("similarityThreshold", PRODUCT_SIMILARITY_THRESHOLD);
        }
    }
}

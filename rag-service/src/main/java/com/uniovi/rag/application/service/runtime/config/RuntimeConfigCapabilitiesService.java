package com.uniovi.rag.application.service.runtime.config;

import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigCapabilitiesResponse;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigCapabilityDto;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Chat-facing capability matrix for manual runtime configuration.
 * <p>
 * This is intentionally explicit and UX-oriented (labels, ordering, requires/excludes) so the frontend can
 * disable controls and prevent invalid/unsupported configs before sending a message.
 */
@Service
public class RuntimeConfigCapabilitiesService {

    public RuntimeConfigCapabilitiesResponse getCapabilities() {
        List<RuntimeConfigCapabilityDto> out = new ArrayList<>();

        out.add(runtimeToggle(
                "useRetrieval",
                "Use retrieval",
                "When enabled, the runtime uses dense retrieval over the knowledge base.",
                10,
                List.of(),
                List.of()));

        out.add(runtimeToggle(
                "naiveFullCorpusInPromptEnabled",
                "Naive full corpus in prompt",
                "When enabled without retrieval, the runtime injects the corpus text directly into the prompt (limited by context window).",
                20,
                List.of(),
                List.of("useRetrieval")));

        out.add(runtimeToggle(
                "expansionEnabled",
                "Query expansion",
                "Optional query expansion before retrieval/tool routing.",
                30,
                List.of(),
                List.of()));

        out.add(runtimeToggle(
                "nerEnabled",
                "NER",
                "Named entity extraction for query understanding and tool/routing improvements.",
                40,
                List.of(),
                List.of()));

        out.add(runtimeToggle(
                "toolsEnabled",
                "Tools",
                "Deterministic tool adapter execution (when a query type is available).",
                50,
                List.of(),
                List.of()));

        out.add(
                new RuntimeConfigCapabilityDto(
                        "functionCallingEnabled",
                        "Function calling",
                        "Spring AI function calling path for tools (ChatClient.tools(adapter)); takes precedence over deterministic tools when both are enabled.",
                        "RUNTIME_HOT_SWAPPABLE",
                        true,
                        true,
                        true,
                        true,
                        null,
                        60,
                        List.of(),
                        List.of(),
                        false,
                        false,
                        "When enabled together with Tools, function calling takes precedence.",
                        null));

        out.add(runtimeToggle(
                "useAdvisor",
                "Advisor",
                "Enables advisor packing. Requires retrieval and a dense retrieval workflow.",
                70,
                List.of("useRetrieval"),
                List.of()));

        out.add(runtimeToggle(
                "reasoningEnabled",
                "Reasoning",
                "Structured answer plan (safe JSON guidance only; no chain-of-thought) used internally before generation.",
                80,
                List.of(),
                List.of()));

        out.add(runtimeToggle(
                "rankerEnabled",
                "Ranker",
                "Deterministic reranking after fusion and before filtering/compression.",
                90,
                List.of("useRetrieval"),
                List.of()));

        out.add(runtimeToggle(
                "postRetrievalEnabled",
                "Post-retrieval",
                "Advanced filtering and evidence-aware compression after reranking.",
                100,
                List.of("useRetrieval"),
                List.of()));

        out.add(indexBound(
                "materializationStrategy",
                "Materialization strategy",
                "Controls how documents/chunks are embedded and retrieved (document-level vs chunk-level).",
                110,
                List.of("useRetrieval"),
                List.of("naiveFullCorpusInPromptEnabled"),
                "Index snapshot compatibility; changing requires reindex."));

        out.add(indexBound(
                "metadataEnabled",
                "Metadata-aware retrieval",
                "Index-level metadata support for metadata-aware retrieval behaviors.",
                120,
                List.of("useRetrieval"),
                List.of(),
                "Index snapshot compatibility; changing requires reindex."));

        out.add(
                runtimeToggle(
                        "clarificationEnabled",
                        "Clarification",
                        "Deterministic clarification loop when the query needs narrowing (multi-turn).",
                        130,
                        List.of(),
                        List.of(),
                        "MULTI_TURN_REQUIRED"));
        out.add(
                runtimeToggle(
                        "memoryEnabled",
                        "Memory",
                        "Uses bounded conversation history / condensation before retrieval when conversation scope exists.",
                        140,
                        List.of(),
                        List.of(),
                        "MULTI_TURN_REQUIRED"));

        out.add(
                runtimeToggle(
                        "adaptiveRoutingEnabled",
                        "Adaptive routing",
                        "Selects among workflow families (direct retrieval, tools, advisor) using deterministic routing gates.",
                        150,
                        List.of(),
                        List.of()));
        out.add(
                runtimeToggle(
                        "judgeEnabled",
                        "Judge",
                        "Post-answer evaluation; workflow answers may trigger one bounded regeneration retry when policy allows.",
                        160,
                        List.of(),
                        List.of()));

        return new RuntimeConfigCapabilitiesResponse(out);
    }

    private static RuntimeConfigCapabilityDto runtimeToggle(
            String key,
            String label,
            String description,
            int displayOrder,
            List<String> requires,
            List<String> excludes) {
        return runtimeToggle(key, label, description, displayOrder, requires, excludes, null);
    }

    private static RuntimeConfigCapabilityDto runtimeToggle(
            String key,
            String label,
            String description,
            int displayOrder,
            List<String> requires,
            List<String> excludes,
            String supportMode) {
        return new RuntimeConfigCapabilityDto(
                key,
                label,
                description,
                "RUNTIME_HOT_SWAPPABLE",
                true,
                true,
                true,
                true,
                supportMode,
                displayOrder,
                requires != null ? List.copyOf(requires) : List.of(),
                excludes != null ? List.copyOf(excludes) : List.of(),
                false,
                false,
                null,
                null);
    }

    private static RuntimeConfigCapabilityDto indexBound(
            String key,
            String label,
            String description,
            int displayOrder,
            List<String> requires,
            List<String> excludes,
            String reasonIfDisabled) {
        return new RuntimeConfigCapabilityDto(
                key,
                label,
                description,
                "INDEX_BOUND",
                true,
                false,
                true,
                true,
                null,
                displayOrder,
                requires != null ? List.copyOf(requires) : List.of(),
                excludes != null ? List.copyOf(excludes) : List.of(),
                true,
                true,
                reasonIfDisabled,
                null);
    }

    // materializationStrategy values are index-bound and surfaced via index profile/snapshot endpoints.
}


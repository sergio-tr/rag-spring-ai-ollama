package com.uniovi.rag.application.service.runtime.config;

import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigCapabilitiesResponse;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigCapabilityDto;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Chat-facing capability matrix for manual runtime configuration.
 * <p>
 * This is intentionally explicit and UX-oriented (labels, groups, requires/excludes) so the frontend can
 * disable controls and prevent invalid/unsupported configs before sending a message.
 */
@Service
public class RuntimeConfigCapabilitiesService {

    public RuntimeConfigCapabilitiesResponse getCapabilities() {
        List<RuntimeConfigCapabilityDto> out = new ArrayList<>();

        out.add(
                cap(
                        "useRetrieval",
                        "Use retrieval",
                        "When enabled, the runtime uses dense retrieval over the knowledge base.",
                        "Retrieval",
                        true,
                        true,
                        List.of(),
                        List.of(),
                        null,
                        Map.of()));

        out.add(
                cap(
                        "naiveFullCorpusInPromptEnabled",
                        "Naive full corpus in prompt",
                        "When enabled without retrieval, the runtime injects the corpus text directly into the prompt (limited by context window).",
                        "Retrieval",
                        true,
                        true,
                        List.of(),
                        List.of("useRetrieval"),
                        null,
                        Map.of()));

        out.add(
                cap(
                        "materializationStrategy",
                        "Materialization strategy",
                        "Controls how documents/chunks are embedded and retrieved (document-level vs chunk-level).",
                        "Retrieval",
                        true,
                        true,
                        List.of("useRetrieval"),
                        List.of("naiveFullCorpusInPromptEnabled"),
                        null,
                        Map.of("allowedValues", materializationValues())));

        out.add(
                cap(
                        "metadataEnabled",
                        "Metadata-aware retrieval",
                        "When enabled with chunk-level retrieval, metadata fields are used for filtering/packing context.",
                        "Retrieval",
                        true,
                        true,
                        List.of("useRetrieval"),
                        List.of(),
                        null,
                        Map.of()));

        out.add(
                cap(
                        "useAdvisor",
                        "Advisor",
                        "Enables advisor packing. Requires retrieval and a dense retrieval workflow.",
                        "Agentic",
                        true,
                        true,
                        List.of("useRetrieval"),
                        List.of(),
                        null,
                        Map.of()));

        Map<String, Object> indexWarn =
                Map.of(
                        "indexHint",
                        "Dense retrieval features require an active index snapshot compatible with materialization (validation warns when missing).");
        out.add(
                cap(
                        "reasoningEnabled",
                        "Reasoning",
                        "Structured answer plan (safe JSON guidance only; no chain-of-thought) used internally before generation.",
                        "Advanced",
                        true,
                        true,
                        List.of(),
                        List.of(),
                        null,
                        indexWarn));
        out.add(
                cap(
                        "rankerEnabled",
                        "Ranker",
                        "Deterministic reranking after fusion and before filtering/compression.",
                        "Advanced",
                        true,
                        true,
                        List.of("useRetrieval"),
                        List.of(),
                        null,
                        indexWarn));
        out.add(
                cap(
                        "postRetrievalEnabled",
                        "Post-retrieval",
                        "Advanced filtering and evidence-aware compression after reranking.",
                        "Advanced",
                        true,
                        true,
                        List.of("useRetrieval"),
                        List.of(),
                        null,
                        indexWarn));

        out.add(
                cap(
                        "clarificationEnabled",
                        "Clarification",
                        "Deterministic clarification loop when the query needs narrowing (multi-turn).",
                        "Multi-turn",
                        true,
                        true,
                        List.of(),
                        List.of(),
                        null,
                        Map.of(
                                "multiTurnHint",
                                "May use multiple turns until the query is specific enough."),
                        "MULTI_TURN_REQUIRED"));
        out.add(
                cap(
                        "memoryEnabled",
                        "Memory",
                        "Uses bounded conversation history / condensation before retrieval when conversation scope exists.",
                        "Multi-turn",
                        true,
                        true,
                        List.of(),
                        List.of(),
                        null,
                        Map.of(
                                "multiTurnHint",
                                "May use multiple turns as the condensed memory updates across messages."),
                        "MULTI_TURN_REQUIRED"));

        out.add(
                cap(
                        "adaptiveRoutingEnabled",
                        "Adaptive routing",
                        "Selects among workflow families (direct retrieval, tools, advisor) using deterministic routing gates.",
                        "Advanced",
                        true,
                        true,
                        List.of(),
                        List.of(),
                        null,
                        Map.of(
                                "routingNote",
                                "When disabled, a compatibility route is used (retrieval vs direct) without classifier-driven switching."),
                        null));
        out.add(
                cap(
                        "judgeEnabled",
                        "Judge",
                        "Post-answer evaluation; workflow answers may trigger one bounded regeneration retry when policy allows.",
                        "Advanced",
                        true,
                        true,
                        List.of(),
                        List.of(),
                        null,
                        Map.of(
                                "defaultMode",
                                "EVALUATE_AND_CONDITIONAL_RETRY",
                                "retryScope",
                                "Workflow answers only (tool-only paths do not retry)"),
                        null));

        return new RuntimeConfigCapabilitiesResponse(out);
    }

    private static RuntimeConfigCapabilityDto cap(
            String key,
            String label,
            String description,
            String group,
            boolean implemented,
            boolean configurable,
            List<String> requires,
            List<String> excludes,
            String reasonIfNotImplemented,
            Map<String, Object> options) {
        return cap(
                key,
                label,
                description,
                group,
                implemented,
                configurable,
                requires,
                excludes,
                reasonIfNotImplemented,
                options,
                null);
    }

    private static RuntimeConfigCapabilityDto cap(
            String key,
            String label,
            String description,
            String group,
            boolean implemented,
            boolean configurable,
            List<String> requires,
            List<String> excludes,
            String reasonIfNotImplemented,
            Map<String, Object> options,
            String supportMode) {
        return new RuntimeConfigCapabilityDto(
                key,
                label,
                description,
                group,
                implemented,
                configurable,
                requires != null ? List.copyOf(requires) : List.of(),
                excludes != null ? List.copyOf(excludes) : List.of(),
                reasonIfNotImplemented,
                options != null ? new LinkedHashMap<>(options) : Map.of(),
                supportMode);
    }

    private static List<String> materializationValues() {
        // Chat supports a subset; STRUCTURED_SEARCH is exposed but will validate as unsupported when combined with retrieval.
        return List.of(
                MaterializationStrategy.DOCUMENT_LEVEL.name(),
                MaterializationStrategy.CHUNK_LEVEL.name(),
                MaterializationStrategy.HYBRID.name(),
                MaterializationStrategy.STRUCTURED_SEARCH.name());
    }
}


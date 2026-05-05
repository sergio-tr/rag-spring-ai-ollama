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

        // Advanced runtime flags (declared but not implemented in the runtime selector).
        out.add(
                cap(
                        "reasoningEnabled",
                        "Reasoning",
                        "Thesis preset flag. Not implemented in the runtime workflow selector.",
                        "Advanced",
                        false,
                        true,
                        List.of(),
                        List.of(),
                        "ADVANCED_RUNTIME_CAPABILITIES_NOT_IMPLEMENTED",
                        Map.of()));
        out.add(
                cap(
                        "rankerEnabled",
                        "Ranker",
                        "Thesis preset flag. Not implemented in the runtime workflow selector.",
                        "Advanced",
                        false,
                        true,
                        List.of("useRetrieval"),
                        List.of(),
                        "ADVANCED_RUNTIME_CAPABILITIES_NOT_IMPLEMENTED",
                        Map.of()));
        out.add(
                cap(
                        "postRetrievalEnabled",
                        "Post-retrieval",
                        "Thesis preset flag. Not implemented in the runtime workflow selector.",
                        "Advanced",
                        false,
                        true,
                        List.of("useRetrieval"),
                        List.of(),
                        "ADVANCED_RUNTIME_CAPABILITIES_NOT_IMPLEMENTED",
                        Map.of()));

        // Multi-turn features exist in the config but Chat does not support the harness yet.
        out.add(
                cap(
                        "clarificationEnabled",
                        "Clarification",
                        "Multi-turn clarification loop. Not supported in Chat UX yet.",
                        "Multi-turn",
                        false,
                        true,
                        List.of(),
                        List.of(),
                        "REQUIRES_MULTI_TURN",
                        Map.of()));
        out.add(
                cap(
                        "memoryEnabled",
                        "Memory",
                        "Conversation memory condensation. Not supported in Chat UX yet.",
                        "Multi-turn",
                        false,
                        true,
                        List.of(),
                        List.of(),
                        "REQUIRES_MULTI_TURN",
                        Map.of()));

        // Exposed for future wiring; currently treated as not implemented.
        out.add(
                cap(
                        "adaptiveRoutingEnabled",
                        "Adaptive routing",
                        "Route selection based on classifier/heuristics. Not implemented in Chat runtime.",
                        "Advanced",
                        false,
                        true,
                        List.of(),
                        List.of(),
                        "NOT_IMPLEMENTED",
                        Map.of()));
        out.add(
                cap(
                        "judgeEnabled",
                        "Judge",
                        "Evaluation-only judging. Not implemented in Chat runtime.",
                        "Advanced",
                        false,
                        true,
                        List.of(),
                        List.of(),
                        "NOT_IMPLEMENTED",
                        Map.of()));

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
                options != null ? new LinkedHashMap<>(options) : Map.of());
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


package com.uniovi.rag.application.service.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.uniovi.rag.application.config.RuntimeConfigResolutionInput;
import com.uniovi.rag.domain.config.runtime.ConfigProfileType;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.domain.knowledge.KnowledgeOperationKind;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Canonical application input for config-aware knowledge rebuild/reindex.
 */
public record KnowledgeConfigurationOperationInput(
        UUID projectId,
        CorpusScope corpusScope,
        UUID conversationId,
        KnowledgeOperationKind operationKind,
        UUID explicitResolvedConfigSnapshotId,
        UUID presetId,
        JsonNode runtimeOverride,
        Set<ConfigProfileType> touchedProfileTypes,
        UUID userId,
        String correlationId) {

    public KnowledgeConfigurationOperationInput {
        touchedProfileTypes = touchedProfileTypes == null ? Set.of() : Set.copyOf(touchedProfileTypes);
    }

    public RuntimeConfigResolutionInput toRuntimeConfigResolutionInput() {
        Optional<JsonNode> override =
                runtimeOverride == null || runtimeOverride.isNull() || runtimeOverride.isEmpty()
                        ? Optional.empty()
                        : Optional.of(runtimeOverride);
        Optional<UUID> conv =
                corpusScope == CorpusScope.CHAT_LOCAL
                        ? Optional.ofNullable(conversationId)
                        : Optional.empty();
        Optional<String> corr =
                correlationId == null || correlationId.isBlank() ? Optional.empty() : Optional.of(correlationId);
        return new RuntimeConfigResolutionInput(
                userId,
                projectId,
                conv,
                Optional.ofNullable(presetId),
                Optional.empty(),
                override,
                touchedProfileTypes,
                Optional.empty(),
                Optional.empty(),
                corr);
    }
}

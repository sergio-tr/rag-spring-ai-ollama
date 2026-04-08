package com.uniovi.rag.domain.runtime.retrieval;

import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record RetrievalRequest(
        String queryText,
        Map<String, String> slots,
        List<String> targetEntities,
        List<String> targetAttributes,
        EntityExtractionResult entities,
        RetrievalMode mode,
        int topKDense,
        int topKSparse,
        int fusionOutputCap,
        int postFusionCap,
        int maxContextChars,
        int denseFetchLimit,
        List<UUID> snapshotIds,
        UUID projectId,
        Optional<String> conversationId,
        List<String> documentAllowlist,
        boolean documentAllowlistIsAll) {

    public RetrievalRequest {
        queryText = Objects.requireNonNull(queryText, "queryText");
        slots = Map.copyOf(Objects.requireNonNull(slots, "slots"));
        targetEntities = List.copyOf(Objects.requireNonNull(targetEntities, "targetEntities"));
        targetAttributes = List.copyOf(Objects.requireNonNull(targetAttributes, "targetAttributes"));
        entities = Objects.requireNonNull(entities, "entities");
        mode = Objects.requireNonNull(mode, "mode");
        snapshotIds = List.copyOf(Objects.requireNonNull(snapshotIds, "snapshotIds"));
        conversationId = conversationId == null ? Optional.empty() : conversationId;
        documentAllowlist = List.copyOf(Objects.requireNonNull(documentAllowlist, "documentAllowlist"));
    }
}

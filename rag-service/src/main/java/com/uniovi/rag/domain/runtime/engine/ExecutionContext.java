package com.uniovi.rag.domain.runtime.engine;

import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Canonical immutable context for one orchestrated RAG turn. Constructed only by {@code ExecutionContextFactory}.
 */
public record ExecutionContext(
        UUID userId,
        UUID projectId,
        UUID conversationId,
        String userQuery,
        RuntimeOperationKind operationKind,
        ResolvedRuntimeConfig resolved,
        String effectiveSystemPrompt,
        KnowledgeSnapshotSelection knowledgeSnapshotSelection,
        Optional<String> configHash,
        Optional<UUID> pinnedResolvedConfigSnapshotId,
        String correlationId,
        List<String> documentFilter,
        Optional<String> chatModelOverride) {

    public ExecutionContext {
        documentFilter = List.copyOf(documentFilter);
        configHash = configHash == null ? Optional.empty() : configHash;
        pinnedResolvedConfigSnapshotId =
                pinnedResolvedConfigSnapshotId == null ? Optional.empty() : pinnedResolvedConfigSnapshotId;
        chatModelOverride = chatModelOverride == null ? Optional.empty() : chatModelOverride;
    }

    /**
     * Stateless legacy HTTP path: no conversation id.
     */
    public boolean stateless() {
        return conversationId == null;
    }
}

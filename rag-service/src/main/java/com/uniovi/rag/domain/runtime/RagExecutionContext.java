package com.uniovi.rag.domain.runtime;

import java.util.List;

/**
 * Per-request execution context flowing through the RAG pipeline (Bloque 1.4).
 *
 * @param conversationId optional conversation id when chat persistence is enabled
 * @param userId         optional user id (null for unscoped execution)
 * @param projectId      optional project scope for retrieval filtering
 * @param resolvedConfig merged {@link RagConfig}
 * @param documentFilter list of document UUIDs to restrict retrieval, or "all" semantics when empty/sentinel
 * @param traceId        correlation id for logs/tracing
 */
public record RagExecutionContext(
        String conversationId,
        String userId,
        String projectId,
        RagConfig resolvedConfig,
        List<String> documentFilter,
        String traceId
) {

    public static final String ALL_DOCUMENTS = "all";

    /** Unscoped execution: no conversation/user/project; retrieval uses {@link #ALL_DOCUMENTS} sentinel. */
    public static RagExecutionContext forUnscopedExecution(RagConfig resolvedConfig, String traceId) {
        return new RagExecutionContext(null, null, null, resolvedConfig, List.of(ALL_DOCUMENTS), traceId);
    }

    public boolean restrictsByProject() {
        return projectId != null && !projectId.isBlank();
    }

    /**
     * When {@code true}, retrieval should include any chunk without project metadata (existing data).
     */
    public boolean documentFilterIsAll() {
        return documentFilter == null
                || documentFilter.isEmpty()
                || documentFilter.stream().anyMatch(ALL_DOCUMENTS::equalsIgnoreCase);
    }
}

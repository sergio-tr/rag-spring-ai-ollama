package com.uniovi.rag.application.service.runtime.tracereplay;

import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;

import java.util.List;
import java.util.UUID;

/**
 * Pinned execution inputs derived from the persisted trace (P18). Not a second routing decision.
 */
public record PinnedReplayExecutionSpec(
        AdaptiveRouteKind routeFamily,
        String workflowName,
        List<UUID> knowledgeSnapshotIds,
        String deterministicToolKind) {

    public PinnedReplayExecutionSpec {
        knowledgeSnapshotIds = List.copyOf(knowledgeSnapshotIds == null ? List.of() : knowledgeSnapshotIds);
        workflowName = workflowName == null ? "" : workflowName;
        deterministicToolKind = deterministicToolKind == null ? "" : deterministicToolKind;
    }
}

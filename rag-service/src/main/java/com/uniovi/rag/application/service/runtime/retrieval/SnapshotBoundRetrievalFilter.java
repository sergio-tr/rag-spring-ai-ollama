package com.uniovi.rag.application.service.runtime.retrieval;

import com.uniovi.rag.domain.runtime.RagExecutionContext;
import com.uniovi.rag.domain.runtime.RagExecutionContextHolder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

/**
 * Builds Spring AI metadata filters for snapshot-bound dense retrieval (evaluation corpus + project chat).
 */
final class SnapshotBoundRetrievalFilter {

    private SnapshotBoundRetrievalFilter() {}

    static Filter.Expression buildForRequest(List<UUID> snapshotIds) {
        if (snapshotIds == null || snapshotIds.isEmpty()) {
            return null;
        }
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        FilterExpressionBuilder.Op snapshotOp = snapshotFilter(b, snapshotIds);
        if (snapshotOp == null) {
            return null;
        }
        RagExecutionContext ctx = RagExecutionContextHolder.get();
        if (ctx != null && ctx.restrictsByProject() && ctx.projectId() != null && !ctx.projectId().isBlank()) {
            FilterExpressionBuilder.Op projectOp = b.eq("projectId", ctx.projectId().trim());
            return b.and(snapshotOp, projectOp).build();
        }
        return snapshotOp.build();
    }

    private static FilterExpressionBuilder.Op snapshotFilter(FilterExpressionBuilder b, List<UUID> snapshotIds) {
        List<String> ids = new ArrayList<>();
        for (UUID id : snapshotIds) {
            if (id != null) {
                ids.add(id.toString());
            }
        }
        if (ids.isEmpty()) {
            return null;
        }
        if (ids.size() == 1) {
            return b.eq("indexSnapshotId", ids.getFirst());
        }
        return b.in("indexSnapshotId", ids);
    }
}

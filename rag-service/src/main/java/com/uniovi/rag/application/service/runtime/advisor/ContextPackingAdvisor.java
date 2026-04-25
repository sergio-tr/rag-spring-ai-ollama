package com.uniovi.rag.application.service.runtime.advisor;

import com.uniovi.rag.domain.runtime.advisor.PackedContextBlock;
import com.uniovi.rag.domain.runtime.advisor.PackedContextSet;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.retrieval.CuratedContextSet;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Builds {@link PackedContextSet} from curated retrieval output (deterministic, extractive).
 */
@Service
public class ContextPackingAdvisor {

    public static final String PACKING_STRATEGY_ID = "P10_CONTEXT_PACKING_V1";

    public PackedContextSet pack(ExecutionContext ctx, QueryPlan plan, CuratedContextSet curated, String workflowName) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(curated, "curated");
        List<RetrievalCandidate> candidates = curated.finalCandidates();
        List<PackedContextBlock> blocks = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            RetrievalCandidate c = candidates.get(i);
            String documentId = String.valueOf(c.metadata().getOrDefault("documentId", ""));
            PackedContextBlock block =
                    new PackedContextBlock(
                            documentId.isEmpty() ? c.candidateId() : documentId,
                            documentId,
                            c.candidateId(),
                            c.snapshotId(),
                            c.content(),
                            i,
                            List.of());
            blocks.add(block);
        }
        Set<String> distinctDocs = new HashSet<>();
        for (PackedContextBlock b : blocks) {
            if (!b.documentId().isEmpty()) {
                distinctDocs.add(b.documentId());
            }
        }
        int totalSources = distinctDocs.isEmpty() ? blocks.size() : distinctDocs.size();
        String promptText = curated.promptContextText();
        return new PackedContextSet(
                blocks,
                PACKING_STRATEGY_ID,
                totalSources,
                blocks.size(),
                List.copyOf(curated.traceNotes()),
                promptText);
    }
}

package com.uniovi.rag.application.service.runtime.advisor;

import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.application.service.runtime.advisor.MetadataToolContextAssembler;
import com.uniovi.rag.application.service.runtime.optimization.DeterministicToolPromptBudgetPolicy;
import com.uniovi.rag.application.service.runtime.tool.DeterministicToolEvidenceHolder;
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

    private final MetadataToolContextAssembler metadataToolContextAssembler;

    public ContextPackingAdvisor(MetadataToolContextAssembler metadataToolContextAssembler) {
        this.metadataToolContextAssembler = metadataToolContextAssembler;
    }

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
        blocks = diversifyForListQuestions(plan, blocks);
        blocks = deprioritizeFooterBlocks(blocks);
        Set<String> distinctDocs = new HashSet<>();
        for (PackedContextBlock b : blocks) {
            if (!b.documentId().isEmpty()) {
                distinctDocs.add(b.documentId());
            }
        }
        int totalSources = distinctDocs.isEmpty() ? blocks.size() : distinctDocs.size();
        String promptText = curated.promptContextText();
        if (DeterministicToolPromptBudgetPolicy.shouldUseToolScopedContext(plan, workflowName)) {
            promptText =
                    DeterministicToolEvidenceHolder.get()
                            .map(DeterministicToolEvidenceHolder.Evidence::assembledContextText)
                            .filter(s -> s != null && !s.isBlank())
                            .orElse(promptText);
            promptText = DeterministicToolPromptBudgetPolicy.budgetPrimaryAnswerContext(promptText).textUsed();
        }
        return new PackedContextSet(
                blocks,
                PACKING_STRATEGY_ID,
                totalSources,
                blocks.size(),
                List.copyOf(curated.traceNotes()),
                promptText);
    }

    private static List<PackedContextBlock> diversifyForListQuestions(
            QueryPlan plan, List<PackedContextBlock> blocks) {
        if (plan == null || blocks == null || blocks.size() < 3) {
            return blocks;
        }
        boolean listQuestion =
                plan.classifierQueryType()
                        .filter(
                                t ->
                                        t == QueryType.FILTER_AND_LIST
                                                || t == QueryType.COUNT_DOCUMENTS
                                                || t == QueryType.FIND_PARAGRAPH)
                        .isPresent();
        if (!listQuestion) {
            return blocks;
        }
        List<PackedContextBlock> diversified = new ArrayList<>();
        Set<String> seenDocs = new HashSet<>();
        for (PackedContextBlock block : blocks) {
            String doc = block.documentId() != null ? block.documentId() : "";
            if (!doc.isEmpty() && seenDocs.contains(doc)) {
                continue;
            }
            if (!doc.isEmpty()) {
                seenDocs.add(doc);
            }
            diversified.add(block);
        }
        for (PackedContextBlock block : blocks) {
            if (diversified.size() >= blocks.size()) {
                break;
            }
            if (!diversified.contains(block)) {
                diversified.add(block);
            }
        }
        for (int i = 0; i < diversified.size(); i++) {
            PackedContextBlock b = diversified.get(i);
            diversified.set(
                    i,
                    new PackedContextBlock(
                            b.sourceId(),
                            b.documentId(),
                            b.blockId(),
                            b.snapshotId(),
                            b.blockText(),
                            i,
                            b.packingNotes()));
        }
        return diversified;
    }

    private List<PackedContextBlock> deprioritizeFooterBlocks(List<PackedContextBlock> blocks) {
        if (blocks == null || blocks.size() < 2) {
            return blocks;
        }
        List<PackedContextBlock> body = new ArrayList<>();
        List<PackedContextBlock> footers = new ArrayList<>();
        for (PackedContextBlock block : blocks) {
            if (MetadataToolContextAssembler.isFooterBoilerplateChunk(block.blockText())) {
                footers.add(block);
            } else {
                body.add(block);
            }
        }
        if (footers.isEmpty()) {
            return blocks;
        }
        body.addAll(footers);
        for (int i = 0; i < body.size(); i++) {
            PackedContextBlock b = body.get(i);
            body.set(
                    i,
                    new PackedContextBlock(
                            b.sourceId(),
                            b.documentId(),
                            b.blockId(),
                            b.snapshotId(),
                            b.blockText(),
                            i,
                            b.packingNotes()));
        }
        return body;
    }
}

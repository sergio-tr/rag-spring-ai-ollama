package com.uniovi.rag.application.service.evaluation.corpus;

import com.uniovi.rag.domain.evaluation.workbook.ChunkRegistryEntry;
import com.uniovi.rag.domain.evaluation.workbook.CorpusDocument;
import com.uniovi.rag.domain.evaluation.workbook.EmbeddingRetrievalQuery;
import com.uniovi.rag.domain.evaluation.workbook.EvaluationWorkbook;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Pre-index checks: workbook gold ids must resolve against corpus_documents and chunk_registry. */
public final class EvaluationGoldCorpusAlignmentVerifier {

    private EvaluationGoldCorpusAlignmentVerifier() {}

    public record AlignmentReport(boolean aligned, List<String> violations) {
        public static AlignmentReport ok() {
            return new AlignmentReport(true, List.of());
        }

        public static AlignmentReport fail(List<String> violations) {
            return new AlignmentReport(false, List.copyOf(violations));
        }
    }

    public static AlignmentReport verifyWorkbook(EvaluationWorkbook workbook) {
        if (workbook == null) {
            return AlignmentReport.fail(List.of("workbook is null"));
        }
        Set<String> corpusDocIds = new LinkedHashSet<>();
        for (CorpusDocument d : workbook.corpusDocuments()) {
            if (d != null && d.documentId() != null && !d.documentId().isBlank()) {
                corpusDocIds.add(normalize(d.documentId()));
            }
        }
        Set<String> registryChunkIds = new LinkedHashSet<>();
        Set<String> registryDocIds = new LinkedHashSet<>();
        for (ChunkRegistryEntry e : workbook.chunkRegistry()) {
            if (e == null) {
                continue;
            }
            if (e.chunkId() != null && !e.chunkId().isBlank()) {
                registryChunkIds.add(normalize(e.chunkId()));
            }
            if (e.documentId() != null && !e.documentId().isBlank()) {
                registryDocIds.add(normalize(e.documentId()));
            }
        }
        List<String> violations = new ArrayList<>();
        for (EmbeddingRetrievalQuery q : workbook.embeddingRetrievalQueries()) {
            if (q == null) {
                continue;
            }
            String qid = q.id() != null ? q.id() : "?";
            for (String rawDoc : q.goldDocumentIds()) {
                String docId = normalize(rawDoc);
                if (docId.isEmpty() || isIgnoredGoldSentinel(docId)) {
                    continue;
                }
                if (!corpusDocIds.contains(docId) && !registryDocIds.contains(docId)) {
                    violations.add("query " + qid + ": unknown gold_document_id " + docId);
                }
            }
            boolean hasDocGold = q.goldDocumentIds() != null
                    && q.goldDocumentIds().stream()
                            .anyMatch(id -> id != null && !id.isBlank() && !isIgnoredGoldSentinel(normalize(id)));
            boolean hasChunkGold = q.goldChunkIds() != null
                    && q.goldChunkIds().stream()
                            .anyMatch(id -> id != null && !id.isBlank() && !isIgnoredGoldSentinel(normalize(id)));
            if (!hasDocGold && !hasChunkGold && !hasOnlyIgnoredGoldSentinels(q)) {
                violations.add("query " + qid + ": missing gold_document_ids and gold_chunk_ids");
            }
            for (String rawChunk : q.goldChunkIds()) {
                String chunkId = normalize(rawChunk);
                if (chunkId.isEmpty() || isIgnoredGoldSentinel(chunkId)) {
                    continue;
                }
                if (!registryChunkIds.contains(chunkId)) {
                    violations.add("query " + qid + ": unknown gold_chunk_id " + chunkId);
                }
            }
        }
        for (ChunkRegistryEntry e : workbook.chunkRegistry()) {
            if (e == null || e.documentId() == null || e.documentId().isBlank()) {
                continue;
            }
            String docId = normalize(e.documentId());
            if (!corpusDocIds.contains(docId) && !registryDocIds.contains(docId)) {
                violations.add("chunk_registry row " + e.chunkId() + ": document_id " + docId + " not in corpus_documents");
            }
            if (e.goldEvidenceText() == null || e.goldEvidenceText().isBlank()) {
                violations.add("chunk_registry row " + e.chunkId() + ": missing gold_evidence_text");
            }
        }
        if (violations.isEmpty()) {
            return AlignmentReport.ok();
        }
        return AlignmentReport.fail(violations);
    }

    public static Set<String> expectedGoldFilenames(EvaluationWorkbook workbook) {
        Set<String> names = new LinkedHashSet<>();
        if (workbook == null) {
            return names;
        }
        for (ChunkRegistryEntry e : workbook.chunkRegistry()) {
            if (e == null
                    || e.documentId() == null
                    || e.documentId().isBlank()
                    || e.chunkId() == null
                    || e.chunkId().isBlank()
                    || e.goldEvidenceText() == null
                    || e.goldEvidenceText().isBlank()) {
                continue;
            }
            names.add(EvaluationGoldCorpusFilenameSupport.buildFilename(e.documentId(), e.chunkId()));
        }
        return names;
    }

    private static boolean isIgnoredGoldSentinel(String id) {
        return "NONE".equalsIgnoreCase(id.trim()) || "N/A".equalsIgnoreCase(id.trim());
    }

    private static boolean hasOnlyIgnoredGoldSentinels(EmbeddingRetrievalQuery q) {
        boolean anyDoc = q.goldDocumentIds() != null && !q.goldDocumentIds().isEmpty();
        boolean anyChunk = q.goldChunkIds() != null && !q.goldChunkIds().isEmpty();
        if (!anyDoc && !anyChunk) {
            return false;
        }
        if (anyDoc) {
            for (String id : q.goldDocumentIds()) {
                if (id != null && !id.isBlank() && !isIgnoredGoldSentinel(id.trim())) {
                    return false;
                }
            }
        }
        if (anyChunk) {
            for (String id : q.goldChunkIds()) {
                if (id != null && !id.isBlank() && !isIgnoredGoldSentinel(id.trim())) {
                    return false;
                }
            }
        }
        return true;
    }

    private static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }
}

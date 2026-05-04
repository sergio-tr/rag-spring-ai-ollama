package com.uniovi.rag.application.evaluation.workbook;

import com.uniovi.rag.domain.evaluation.workbook.EvaluationWorkbook;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Row counts extracted from a parsed reference workbook for observability (Lab status, logs).
 */
public record ReferenceBundleCounts(
        int llmReaderQuestions,
        int embeddingRetrievalQueries,
        int ragPresetQuestions,
        int corpusDocuments,
        int chunkRegistryEntries,
        int presets,
        int modelCandidates) {

    public static ReferenceBundleCounts fromWorkbook(EvaluationWorkbook wb) {
        if (wb == null) {
            return empty();
        }
        return new ReferenceBundleCounts(
                wb.llmReaderQuestions().size(),
                wb.embeddingRetrievalQueries().size(),
                wb.ragPresetQuestionsEnriched().size(),
                wb.corpusDocuments().size(),
                wb.chunkRegistry().size(),
                wb.ragPresetCatalog().size(),
                wb.llmCandidates().size());
    }

    public static ReferenceBundleCounts empty() {
        return new ReferenceBundleCounts(0, 0, 0, 0, 0, 0, 0);
    }

    /** Stable keys for JSON under {@code countsByDatasetKind}. */
    public Map<String, Integer> toDatasetKindMap() {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("llmReaderQuestions", llmReaderQuestions);
        m.put("embeddingRetrievalQueries", embeddingRetrievalQueries);
        m.put("ragPresetQuestions", ragPresetQuestions);
        m.put("corpusDocuments", corpusDocuments);
        m.put("chunkRegistryEntries", chunkRegistryEntries);
        m.put("presets", presets);
        m.put("modelCandidates", modelCandidates);
        return Collections.unmodifiableMap(m);
    }
}

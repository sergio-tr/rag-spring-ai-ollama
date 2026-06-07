package com.uniovi.rag.domain.evaluation.workbook;

import java.util.Map;

/** Row from {@code corpus_documents} sheet. */
public record CorpusDocument(
        String documentId,
        Map<String, String> additionalColumns) {

    public CorpusDocument {
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId required");
        }
    }
}

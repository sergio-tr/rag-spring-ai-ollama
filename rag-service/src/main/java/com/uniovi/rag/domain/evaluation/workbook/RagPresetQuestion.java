package com.uniovi.rag.domain.evaluation.workbook;

import com.uniovi.rag.domain.model.QueryType;

import java.util.List;
import java.util.Optional;

/** Row from {@code rag_preset_questions_enriched} sheet. */
public record RagPresetQuestion(
        String id,
        String question,
        String expectedAnswer,
        Optional<QueryType> queryType,
        Optional<DifficultyLevel> difficulty,
        String answerMode,
        List<String> goldDocumentIds,
        List<String> goldChunkIds,
        String expectedEvidenceCount,
        boolean unanswerable,
        boolean requiresMultiDocument,
        boolean requiresTemporalReasoning,
        boolean requiresAggregation,
        boolean requiresExactEntities,
        String notes) {

    public RagPresetQuestion {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id required");
        }
        question = question != null ? question : "";
        goldDocumentIds = goldDocumentIds != null ? List.copyOf(goldDocumentIds) : List.of();
        goldChunkIds = goldChunkIds != null ? List.copyOf(goldChunkIds) : List.of();
    }
}

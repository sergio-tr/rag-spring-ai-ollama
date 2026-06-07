package com.uniovi.rag.domain.evaluation.workbook;

import com.uniovi.rag.domain.model.QueryType;

import java.util.Optional;

/** Row from {@code llm_reader_questions} sheet. */
public record LlmReaderQuestion(
        String id,
        String question,
        String contextText,
        String expectedAnswer,
        Optional<QueryType> queryType,
        Optional<DifficultyLevel> difficulty,
        String answerMode,
        String sourceDocumentId,
        String goldEvidence,
        boolean unanswerable,
        String evaluationMethod) {

    public LlmReaderQuestion {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id required");
        }
        question = question != null ? question : "";
    }
}

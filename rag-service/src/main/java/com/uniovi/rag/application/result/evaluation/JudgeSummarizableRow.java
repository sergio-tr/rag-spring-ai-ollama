package com.uniovi.rag.application.result.evaluation;

import java.util.List;

/**
 * Minimal row shape required to aggregate LLM-judge benchmark metrics.
 */
public interface JudgeSummarizableRow {

    String llmEvaluation();

    String correctAnswer();

    String generatedAnswer();

    default List<String> retrievedDocumentIds() {
        return List.of();
    }

    default List<String> relevantDocumentIds() {
        return List.of();
    }
}

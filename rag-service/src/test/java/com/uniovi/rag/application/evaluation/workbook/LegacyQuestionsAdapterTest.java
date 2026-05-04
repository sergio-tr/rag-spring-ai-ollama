package com.uniovi.rag.application.evaluation.workbook;

import com.uniovi.rag.domain.evaluation.workbook.LlmReaderQuestion;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyQuestionsAdapterTest {

    @Test
    void toQuestionAnswerMap_usesQuestionTextAsKey() {
        List<LlmReaderQuestion> rows =
                List.of(row("id-1", "What is X?", "Answer X"), row("id-2", "What is Y?", "Answer Y"));
        Map<String, String> m = LegacyQuestionsAdapter.toQuestionAnswerMap(rows);
        assertThat(m).containsEntry("What is X?", "Answer X").containsEntry("What is Y?", "Answer Y");
    }

    @Test
    void duplicateQuestionText_lastRowWins() {
        List<LlmReaderQuestion> rows =
                List.of(row("a", "Dup?", "first"), row("b", "Dup?", "second"));
        Map<String, String> m = LegacyQuestionsAdapter.toQuestionAnswerMap(rows);
        assertThat(m).hasSize(1).containsEntry("Dup?", "second");
    }

    @Test
    void skipsEmptyQuestionText() {
        List<LlmReaderQuestion> rows = List.of(row("a", "", "ans"), row("b", "Ok?", "yes"));
        Map<String, String> m = LegacyQuestionsAdapter.toQuestionAnswerMap(rows);
        assertThat(m).containsOnlyKeys("Ok?");
    }

    private static LlmReaderQuestion row(String id, String question, String expectedAnswer) {
        return new LlmReaderQuestion(
                id,
                question,
                "",
                expectedAnswer,
                Optional.empty(),
                Optional.empty(),
                "",
                "",
                "",
                false,
                "");
    }
}

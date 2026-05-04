package com.uniovi.rag.application.evaluation.workbook;

import com.uniovi.rag.domain.evaluation.workbook.LlmReaderQuestion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Historical projection from typed {@link LlmReaderQuestion} rows to a {@code Map&lt;String,String&gt;} ({@code question
 * text → expected answer}) for tests and migration tooling only.
 *
 * <p><strong>Compatibility policy (collision):</strong> the map key is always {@link LlmReaderQuestion#question()}
 * text (trimmed), not {@link LlmReaderQuestion#id()}. If two rows share the same non-empty question text, a structured
 * WARN is logged ({@code event}, {@code questionText}, {@code newQuestionId}) and the <strong>last</strong> row in
 * iteration order wins.
 *
 * <p>Production benchmark paths must use typed lists and {@link com.uniovi.rag.application.service.evaluation.ExperimentalDatasetResolver}; do not wire this adapter into runtime beans.
 */
public final class LegacyQuestionsAdapter {

    private static final Logger log = LoggerFactory.getLogger(LegacyQuestionsAdapter.class);

    public static final String LOG_EVENT_DUPLICATE_QUESTION_TEXT = "legacy_questions_duplicate_question_text";

    private LegacyQuestionsAdapter() {}

    /**
     * @param rows parsed {@code llm_reader_questions} rows (order preserved for last-wins collisions)
     * @return mutable legacy map; empty questions are skipped
     */
    public static Map<String, String> toQuestionAnswerMap(List<LlmReaderQuestion> rows) {
        Map<String, String> out = new LinkedHashMap<>();
        if (rows == null) {
            return out;
        }
        for (LlmReaderQuestion q : rows) {
            if (q == null) {
                continue;
            }
            String key = q.question() != null ? q.question().trim() : "";
            if (key.isEmpty()) {
                continue;
            }
            String answer = q.expectedAnswer() != null ? q.expectedAnswer().trim() : "";
            if (out.containsKey(key)) {
                log.warn(
                        "Duplicate question text in legacy projection; keeping last row event={} questionText={} newQuestionId={}",
                        LOG_EVENT_DUPLICATE_QUESTION_TEXT,
                        truncateForLog(key),
                        q.id());
            }
            out.put(key, answer);
        }
        return out;
    }

    private static String truncateForLog(String s) {
        if (s.length() <= 120) {
            return s;
        }
        return s.substring(0, 117) + "...";
    }
}

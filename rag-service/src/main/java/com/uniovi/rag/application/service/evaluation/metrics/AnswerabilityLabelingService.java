package com.uniovi.rag.application.service.evaluation.metrics;

import com.uniovi.rag.domain.evaluation.workbook.RagPresetQuestion;
import com.uniovi.rag.domain.model.QueryType;
import java.util.Optional;

/** Applies dataset precedence and expected-answer inference for answerability labels. */
public final class AnswerabilityLabelingService {

    private AnswerabilityLabelingService() {}

    public static String rulesVersion() {
        return AnswerabilityLabelRules.RULES_VERSION;
    }

    public static AnswerabilityLabelResult label(RagPresetQuestion question) {
        if (question == null) {
            return defaultUnknown("");
        }
        return label(
                question.expectedAnswer(),
                question.queryType().orElse(null),
                question.unanswerable(),
                question.unanswerableDeclared(),
                question.ambiguous(),
                question.ambiguousDeclared());
    }

    public static AnswerabilityLabelResult label(
            String expectedAnswer,
            QueryType queryType,
            boolean unanswerable,
            boolean unanswerableDeclared,
            boolean ambiguous,
            boolean ambiguousDeclared) {
        if (ambiguousDeclared && ambiguous) {
            return new AnswerabilityLabelResult(
                    Answerability.AMBIGUOUS,
                    AnswerabilitySource.DATASET_COLUMN,
                    AnswerabilityLabelRules.DATASET_AMBIGUOUS,
                    null,
                    "dataset_ambiguous");
        }
        if (unanswerableDeclared) {
            if (unanswerable) {
                return new AnswerabilityLabelResult(
                        Answerability.UNANSWERABLE,
                        AnswerabilitySource.DATASET_COLUMN,
                        AnswerabilityLabelRules.DATASET_UNANSWERABLE,
                        null,
                        "dataset_unanswerable");
            }
            return new AnswerabilityLabelResult(
                    Answerability.ANSWERABLE,
                    AnswerabilitySource.DATASET_COLUMN,
                    AnswerabilityLabelRules.DATASET_ANSWERABLE,
                    null,
                    "dataset_answerable");
        }
        AnswerabilityLabelResult inferred = AnswerabilityLabelRules.inferFromExpectedAnswer(expectedAnswer, queryType);
        if (inferred.label() == Answerability.NEEDS_REVIEW) {
            return inferred;
        }
        if (inferred.label() == Answerability.UNKNOWN) {
            return defaultUnknown(inferred.reason());
        }
        return inferred;
    }

    private static AnswerabilityLabelResult defaultUnknown(String reason) {
        return new AnswerabilityLabelResult(
                Answerability.UNKNOWN,
                AnswerabilitySource.DEFAULT_UNKNOWN,
                "",
                null,
                reason != null && !reason.isBlank() ? reason : "no_safe_signal");
    }
}

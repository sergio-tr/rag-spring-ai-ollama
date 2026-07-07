package com.uniovi.rag.application.service.runtime.clarification;

import com.uniovi.rag.domain.runtime.clarification.PendingClarificationState;
import org.springframework.stereotype.Component;

/**
 * P11 frozen merge: {@code BASE:<base>\nQUESTION:<q>\nANSWER:<a>} - no trimming, no LLM.
 */
@Component
public class ClarifiedQueryRefiner {

    public String refine(PendingClarificationState pending, String userAnswer) {
        if (pending == null) {
            throw new IllegalArgumentException("pending must not be null");
        }
        String a = userAnswer == null ? "" : userAnswer;
        return "BASE:"
                + pending.baseQueryTextForClarification()
                + "\nQUESTION:"
                + pending.clarificationQuestionText()
                + "\nANSWER:"
                + a;
    }
}

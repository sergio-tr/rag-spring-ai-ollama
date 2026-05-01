package com.uniovi.rag.application.service.runtime.clarification;

import com.uniovi.rag.domain.runtime.clarification.ClarificationQuestion;
import com.uniovi.rag.domain.runtime.clarification.ClarificationQuestionKind;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import org.springframework.stereotype.Component;

/**
 * Maps a selected kind to the frozen template string (P11). No LLM.
 */
@Component
public class ClarificationQuestionGenerator {

    public ClarificationQuestion questionForKind(ClarificationQuestionKind kind, QueryPlan plan) {
        String text = kind.templateText();
        return new ClarificationQuestion(text, kind, plan.ambiguityAssessment().missingFields());
    }
}

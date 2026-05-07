package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.configuration.RagRuntimeProperties;
import org.springframework.stereotype.Service;

/**
 * Shared, character-based prompt budgeting used to prevent hard LLM context-window errors.
 *
 * <p>This budgeter is intentionally conservative and does not attempt token-accurate measurement.
 * It enforces a stable upper bound and returns truncation metadata so callers can persist evidence.</p>
 */
@Service
public class RuntimePromptBudgeter {

    private static final String TRUNC_MARKER = "\n...[context truncated]\n";

    private final RagRuntimeProperties runtime;

    public RuntimePromptBudgeter(RagRuntimeProperties runtime) {
        this.runtime = runtime;
    }

    public BudgetResult budgetForFullCorpus(String corpusText) {
        int max = Math.max(0, safeContext().getFullCorpusMaxChars());
        int global = Math.max(0, safeContext().getMaxPromptChars());
        int applied = Math.min(max, global);
        return truncate("full_corpus", corpusText, applied, "full_corpus_max_chars");
    }

    public BudgetResult budgetForLegacyContext(String contextText) {
        int max = Math.max(0, safeContext().getLegacyContextMaxChars());
        int global = Math.max(0, safeContext().getMaxPromptChars());
        int applied = Math.min(max, global);
        return truncate("legacy_context", contextText, applied, "legacy_context_max_chars");
    }

    public BudgetResult budgetForCombinedDocument(String text) {
        int max = Math.max(0, safeContext().getCombinedDocumentMaxChars());
        return truncate("combined_document", text, max, "combined_document_max_chars");
    }

    public BudgetResult budgetForJudgeCandidateAnswer(String answerText) {
        int max = Math.max(0, safeContext().getJudgeMaxAnswerChars());
        return truncate("judge_candidate_answer", answerText, max, "judge_max_answer_chars");
    }

    private RagRuntimeProperties.Context safeContext() {
        RagRuntimeProperties.Context c = runtime != null ? runtime.getContext() : null;
        return c != null ? c : new RagRuntimeProperties.Context();
    }

    public static BudgetResult truncate(String stage, String raw, int budgetChars, String reason) {
        String full = raw != null ? raw : "";
        String trimmed = full.trim();
        int original = trimmed.length();
        int budget = Math.max(0, budgetChars);
        if (original <= budget) {
            return new BudgetResult(stage, false, original, budget, original, reason, trimmed);
        }
        // head/tail truncation keeps some conclusion signal (useful for minutes), but we must
        // ensure final output does not exceed budget (including truncation marker).
        int markerLen = TRUNC_MARKER.length();
        int available = Math.max(0, budget - markerLen);
        if (available <= 0) {
            String out = trimmed.substring(0, Math.min(original, budget));
            return new BudgetResult(stage, true, original, budget, out.length(), reason, out);
        }
        int head = (int) Math.max(0, Math.floor(available * 0.65));
        int tail = Math.max(0, available - head);
        String out;
        if (tail <= 0) {
            out = trimmed.substring(0, Math.min(original, budget));
        } else if (head <= 0) {
            out = trimmed.substring(Math.max(0, original - tail));
        } else {
            out = trimmed.substring(0, Math.min(original, head))
                    + TRUNC_MARKER
                    + trimmed.substring(Math.max(0, original - tail));
        }
        return new BudgetResult(stage, true, original, budget, out.length(), reason, out);
    }

    public record BudgetResult(
            String stage,
            boolean truncated,
            int originalChars,
            int budgetChars,
            int finalChars,
            String reason,
            String textUsed
    ) {}
}


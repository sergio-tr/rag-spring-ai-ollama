package com.uniovi.rag.service.evaluation.baseline;

/**
 * Documents approximate character budget for {@code LLM_FULL_DOCUMENT_CONTEXT}. Tokens are approximated from
 * {@code numCtx} when provided ({@code chars ≈ numCtx * 4}); otherwise {@code fallbackMaxChars} applies.
 */
public final class DocumentContextTruncator {

    public record Result(String textUsed, boolean truncated, int originalChars, int maxCharsApplied, String note) {}

    private DocumentContextTruncator() {}

    public static Result truncate(String fullText, Integer numCtx, int fallbackMaxChars) {
        String full = fullText != null ? fullText : "";
        int original = full.length();
        int budget =
                numCtx != null && numCtx > 0
                        ? Math.max(1024, numCtx * 4)
                        : Math.max(1024, fallbackMaxChars);
        if (original <= budget) {
            return new Result(full, false, original, budget, "No truncation (within budget numCtx-derived or fallback).");
        }
        String head = full.substring(0, budget);
        return new Result(
                head,
                true,
                original,
                budget,
                "Head truncation applied for LLM_FULL_DOCUMENT_CONTEXT: kept first "
                        + budget
                        + " chars of "
                        + original
                        + " (approximate context budget from numCtx or fallback).");
    }
}

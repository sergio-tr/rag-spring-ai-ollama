package com.uniovi.rag.application.service.runtime.clarification;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves P11 merged clarification payloads into a single planning query for QU (no LLM).
 */
public final class ClarifiedPlanningInputResolver {

    private static final Pattern MERGED =
            Pattern.compile("BASE:(.*?)\\nQUESTION:(.*?)\\nANSWER:(.*)", Pattern.DOTALL);

    private ClarifiedPlanningInputResolver() {}

    public static String resolveForPlanning(String effectiveInput) {
        if (effectiveInput == null || !effectiveInput.contains("BASE:")) {
            return effectiveInput;
        }
        Matcher matcher = MERGED.matcher(effectiveInput);
        if (!matcher.matches()) {
            return effectiveInput;
        }
        String base = matcher.group(1);
        String answer = matcher.group(3);
        if (base == null || answer == null || answer.isBlank()) {
            return effectiveInput;
        }
        String trimmedBase = base.trim();
        String trimmedAnswer = answer.trim();
        if (trimmedBase.isBlank()) {
            return trimmedAnswer;
        }
        if (trimmedBase.endsWith("?")) {
            String stem = trimmedBase.substring(0, trimmedBase.length() - 1).trim();
            return stem + " (" + trimmedAnswer + ")?";
        }
        return trimmedBase + " (" + trimmedAnswer + ")";
    }

    public static boolean isMergedClarificationPayload(String text) {
        return text != null && text.contains("BASE:") && text.contains("ANSWER:");
    }

    public static String extractAnswerAnchor(String effectiveInput) {
        if (!isMergedClarificationPayload(effectiveInput)) {
            return "";
        }
        Matcher matcher = MERGED.matcher(effectiveInput);
        if (!matcher.matches()) {
            return "";
        }
        String answer = matcher.group(3);
        return answer == null ? "" : answer.trim();
    }

    public static boolean answerAnchorLooksLikeDateOrDocument(String answer) {
        if (answer == null || answer.isBlank()) {
            return false;
        }
        String q = answer.toLowerCase(Locale.ROOT);
        return q.matches(".*\\b\\d{4}-\\d{2}-\\d{2}\\b.*")
                || q.matches(".*\\b\\d{1,2}[/-]\\d{1,2}[/-]\\d{4}\\b.*")
                || q.matches(".*\\b\\d{1,2}\\s+de\\s+\\p{L}+\\s+de\\s+\\d{4}\\b.*")
                || q.contains("acta")
                || q.contains("reunión")
                || q.contains("reunion");
    }
}

package com.uniovi.rag.application.service.runtime;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Strips thinking-model reasoning leaks and internal orchestration traces from user-visible answers (BL-008).
 */
public final class ReasoningBlockSanitizer {

    private static final Pattern THINK_TAGS =
            Pattern.compile(
                    "(?is)<\\s*(?:think|redacted_thinking|redacted_reasoning)\\b[^>]*>.*?</\\s*(?:think|redacted_thinking|redacted_reasoning)\\s*>");
    private static final Pattern THINK_TAG_TRAILING =
            Pattern.compile("(?is)<\\s*(?:think|redacted_thinking|redacted_reasoning)\\b[^>]*>.*$");
    private static final Pattern REACT_LABELED_LINE =
            Pattern.compile("(?iu)^\\s*(?:Thought|Reasoning|Action|Observation)\\s*:.*");
    private static final Pattern INTERNAL_JSON_PLAN =
            Pattern.compile(
                    "(?is)\\{\\s*\"(?:workflowName|routingRouteKind|routingOutcome|predictedQueryType|queryTypePredicted|memoryOutcome|classifierLabel)\"\\s*:[^}]{0,2000}\\}");
    private static final Pattern ROUTING_LABELS =
            Pattern.compile(
                    "\\b(?:PARENT_P\\d+|PARENT_P[A-Z_]+|baseline_floor[\\w:]*|RETRIEVAL_WORKFLOW_ROUTE|DETERMINISTIC_TOOL_ROUTE|FUNCTION_CALLING_ROUTE|ADVISOR_ROUTE|deterministic-tool|function-calling|topic_not_in_context|not_in_context|function_sentinel_abstention|native_not_constraint_complete|advanced_preset_parent_floor|outcome=\\w+|routeKind=\\w+)\\b",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern JUDGE_FORMAT_LINE =
            Pattern.compile("(?im)^\\s*(?:Answer|Explanation|FEEDBACK)\\s*:.*$");
    private static final Pattern REDACTED_REASONING_TOKEN =
            Pattern.compile("(?im)^\\s*redacted_reasoning\\s*$");

    private ReasoningBlockSanitizer() {}

    /**
     * Removes reasoning / trace blocks while preserving substantive answer text when present.
     *
     * @return trimmed text; may be empty when only internal trace remained
     */
    public static String stripReasoningBlocks(String text) {
        if (text == null) {
            return null;
        }
        if (text.isBlank()) {
            return text;
        }
        String out = text;
        out = THINK_TAGS.matcher(out).replaceAll("");
        out = THINK_TAG_TRAILING.matcher(out).replaceAll("");
        out = stripReactLabeledLines(out);
        out = JUDGE_FORMAT_LINE.matcher(out).replaceAll("");
        out = INTERNAL_JSON_PLAN.matcher(out).replaceAll("");
        out = ROUTING_LABELS.matcher(out).replaceAll("");
        out = REDACTED_REASONING_TOKEN.matcher(out).replaceAll("");
        out = out.replaceAll("\\s{2,}", " ").trim();
        out = out.replaceAll(" \\.", ".").replaceAll(" ,", ",");
        out = out.replaceAll("^[:;\\-]+\\s*", "").trim();
        return out;
    }

    private static String stripReactLabeledLines(String text) {
        String[] lines = text.split("\\R", -1);
        StringBuilder kept = new StringBuilder();
        for (String line : lines) {
            if (REACT_LABELED_LINE.matcher(line).matches()) {
                continue;
            }
            if (!kept.isEmpty()) {
                kept.append('\n');
            }
            kept.append(line);
        }
        return kept.toString();
    }

    /** Strip reasoning blocks; returns a controlled fallback when nothing factual remains. */
    public static String sanitizeForUser(String text) {
        boolean spanish = text == null || text.isBlank() || looksSpanish(text);
        return sanitizeForUser(text, spanish);
    }

    public static String sanitizeForUser(String text, boolean spanish) {
        String stripped = stripReasoningBlocks(text);
        if (stripped == null || stripped.isBlank()) {
            return controlledFallback(spanish);
        }
        return stripped;
    }

    public static String controlledFallback(boolean spanish) {
        return spanish
                ? RuntimeAnswerPrompts.INSUFFICIENT_DOCUMENT_CONTEXT_MESSAGE_ES
                : RuntimeAnswerPrompts.INSUFFICIENT_DOCUMENT_CONTEXT_MESSAGE_EN;
    }

    public static boolean looksSpanish(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        String q = text.toLowerCase(Locale.ROOT);
        return q.contains("¿")
                || q.contains("acta")
                || q.contains("reunión")
                || q.contains("reunion")
                || q.contains("presidente")
                || q.contains("secretaria")
                || q.contains("secretario")
                || q.contains("asistent")
                || q.contains("no consta")
                || q.contains("razonamiento")
                || q.contains("secretaria fue")
                || q.contains("del acta");
    }
}

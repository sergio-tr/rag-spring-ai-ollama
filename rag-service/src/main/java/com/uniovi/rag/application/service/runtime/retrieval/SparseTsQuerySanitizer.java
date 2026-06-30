package com.uniovi.rag.application.service.runtime.retrieval;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Sanitizes lexical tokens and tsquery strings so PostgreSQL full-text operators ({@code :}, {@code |}, etc.)
 * do not abort the surrounding transaction.
 */
final class SparseTsQuerySanitizer {

    /** Clock times such as {@code 8:30} — colon is invalid inside {@code to_tsquery} atoms. */
    private static final Pattern TIME_TOKEN = Pattern.compile("^\\d{1,2}:\\d{2}$");

    /** Times embedded in a longer websearch/plain query. */
    private static final Pattern TIME_IN_TEXT = Pattern.compile("\\b(\\d{1,2}):(\\d{2})\\b");

    private static final Pattern TSQUERY_SPECIAL = Pattern.compile("[|&!():*<>]");

    private SparseTsQuerySanitizer() {}

    static String sanitizeOrTerm(String term) {
        if (term == null || term.isBlank()) {
            return "";
        }
        String normalized = normalizeTimeToken(term.trim());
        normalized = normalized.replace("'", "''");
        normalized = TSQUERY_SPECIAL.matcher(normalized).replaceAll(" ");
        normalized = normalized.replace(":", " ");
        normalized = normalized.replaceAll("\\s+", " & ").trim();
        if (normalized.isEmpty() || isOnlyOperators(normalized)) {
            return "";
        }
        return normalized;
    }

    static String joinOrTerms(List<String> terms) {
        List<String> sanitized = new ArrayList<>();
        for (String term : terms) {
            if (term == null || term.isBlank()) {
                continue;
            }
            String out = sanitizeOrTerm(term.trim());
            if (!out.isBlank()) {
                sanitized.add(out);
            }
        }
        if (sanitized.isEmpty()) {
            return "";
        }
        return String.join(" | ", sanitized);
    }

    static String sanitizeStageQuery(String queryText, boolean orTsqueryMode) {
        if (queryText == null || queryText.isBlank()) {
            return "";
        }
        String q = queryText.startsWith("ILIKE:") ? queryText.substring("ILIKE:".length()) : queryText;
        q = normalizeTimesInText(q);
        if (orTsqueryMode) {
            return isValidOrTsquery(q) ? q.trim() : "";
        }
        return q.trim();
    }

    static boolean isValidOrTsquery(String orJoined) {
        if (orJoined == null || orJoined.isBlank()) {
            return false;
        }
        if (orJoined.contains(":")) {
            return false;
        }
        boolean sawTerm = false;
        for (String part : orJoined.split("\\|")) {
            String p = part.trim();
            if (p.isEmpty() || isOnlyOperators(p)) {
                continue;
            }
            sawTerm = true;
        }
        return sawTerm;
    }

    private static String normalizeTimeToken(String term) {
        if (TIME_TOKEN.matcher(term).matches()) {
            int colon = term.indexOf(':');
            return term.substring(0, colon) + "h" + term.substring(colon + 1);
        }
        return term;
    }

    private static String normalizeTimesInText(String text) {
        return TIME_IN_TEXT.matcher(text).replaceAll(m -> m.group(1) + "h" + m.group(2));
    }

    private static boolean isOnlyOperators(String value) {
        String compact = value.replace(" ", "").replace("&", "").replace("|", "");
        return compact.isEmpty();
    }
}

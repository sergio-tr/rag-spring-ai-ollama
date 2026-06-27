package com.uniovi.rag.application.service.runtime.memory;

import com.uniovi.rag.domain.MessageRole;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryTurn;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic follow-up expansion for acta-domain demonstratives and pronouns (P12).
 */
public final class ConversationFollowUpResolver {

    private static final Pattern DATE_SLASH = Pattern.compile("\\b\\d{1,2}/\\d{1,2}/\\d{4}\\b");
    private static final Pattern DATE_ISO = Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}\\b");
    private static final Pattern DATE_SPANISH =
            Pattern.compile("\\b\\d{1,2}\\s+de\\s+[\\p{L}]+\\s+de\\s+\\d{4}\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private ConversationFollowUpResolver() {}

    public static Optional<String> expand(List<ConversationMemoryTurn> history, String latestUserTurn) {
        if (history == null || history.isEmpty() || latestUserTurn == null || latestUserTurn.isBlank()) {
            return Optional.empty();
        }
        String lower = latestUserTurn.toLowerCase(Locale.ROOT);
        if (!needsExpansion(lower)) {
            return Optional.empty();
        }
        Optional<String> anchorDate = findMostRecentDate(history);
        if (anchorDate.isEmpty()) {
            return Optional.empty();
        }
        String date = anchorDate.get();
        String expanded = latestUserTurn;
        expanded =
                expanded.replaceAll("(?iu)\\besa reunión\\b", "la reunión del " + date)
                        .replaceAll("(?iu)\\bese acta\\b", "el acta del " + date)
                        .replaceAll("(?iu)\\besa acta\\b", "la acta del " + date)
                        .replaceAll("(?iu)\\besa fecha\\b", "la fecha " + date);
        if (lower.contains("ellos")
                && (mentionsParticipants(lower) || lower.contains("quién") || lower.contains("quien"))) {
            expanded = expanded.replaceAll("(?iu)\\bellos\\b", "los participantes de la reunión del " + date);
        }
        if (lower.contains("los participantes") && !containsDate(lower)) {
            expanded =
                    expanded.replaceAll(
                            "(?iu)\\blos participantes\\b",
                            "los participantes de la reunión del " + date);
        }
        if (expanded.equals(latestUserTurn)) {
            return Optional.empty();
        }
        return Optional.of(expanded.trim());
    }

    private static boolean needsExpansion(String lower) {
        return lower.contains("esa reunión")
                || lower.contains("ese acta")
                || lower.contains("esa acta")
                || lower.contains("esa fecha")
                || lower.contains("ellos")
                || (lower.contains("los participantes") && !containsDate(lower));
    }

    private static boolean mentionsParticipants(String lower) {
        return lower.contains("participantes") || lower.contains("asistieron") || lower.contains("asistentes");
    }

    private static boolean containsDate(String lower) {
        return DATE_SLASH.matcher(lower).find()
                || DATE_ISO.matcher(lower).find()
                || DATE_SPANISH.matcher(lower).find();
    }

    static Optional<String> findMostRecentDate(List<ConversationMemoryTurn> history) {
        Optional<String> fromUser = findMostRecentDateInRole(history, MessageRole.USER);
        if (fromUser.isPresent()) {
            return fromUser;
        }
        return findMostRecentDateInRole(history, null);
    }

    private static Optional<String> findMostRecentDateInRole(
            List<ConversationMemoryTurn> history, MessageRole roleFilter) {
        for (int i = history.size() - 1; i >= 0; i--) {
            ConversationMemoryTurn turn = history.get(i);
            if (turn == null || turn.content() == null) {
                continue;
            }
            if (roleFilter != null && turn.role() != roleFilter) {
                continue;
            }
            Optional<String> fromContent = firstDateInText(turn.content());
            if (fromContent.isPresent()) {
                return fromContent;
            }
        }
        return Optional.empty();
    }

    private static Optional<String> firstDateInText(String text) {
        Matcher slash = DATE_SLASH.matcher(text);
        if (slash.find()) {
            return Optional.of(slash.group());
        }
        Matcher iso = DATE_ISO.matcher(text);
        if (iso.find()) {
            return Optional.of(iso.group());
        }
        Matcher spanish = DATE_SPANISH.matcher(text);
        if (spanish.find()) {
            return Optional.of(spanish.group());
        }
        return Optional.empty();
    }
}

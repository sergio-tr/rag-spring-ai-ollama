package com.uniovi.rag.application.service.runtime.memory;

import com.uniovi.rag.domain.MessageRole;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryTurn;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic follow-up expansion for acta-domain demonstratives, roles, and time anchors.
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
        if (containsDate(lower)) {
            return Optional.empty();
        }
        if (!needsExpansion(lower)) {
            return Optional.empty();
        }

        Optional<String> anchorDate = resolveAnchorDate(history, lower);
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

        if (isActaStructuredFieldFollowUp(lower) && !containsDate(expanded.toLowerCase(Locale.ROOT))) {
            expanded = expanded.trim() + " en el acta del " + date;
        }

        if (expanded.equals(latestUserTurn)) {
            return Optional.empty();
        }
        return Optional.of(expanded.trim());
    }

    static Optional<String> resolveAnchorDate(List<ConversationMemoryTurn> history, String lowerQuery) {
        if (requiresUniqueAnchorDate(lowerQuery)) {
            Set<String> structuredAnchors = collectStructuredAnchorDates(history);
            if (structuredAnchors.size() > 1) {
                return Optional.empty();
            }
            if (structuredAnchors.size() == 1) {
                return Optional.of(structuredAnchors.iterator().next());
            }
            Optional<String> unique = findUniqueAnchorDate(history);
            if (unique.isPresent()) {
                return unique;
            }
            // when multiple text dates exist, prefer the latest user-stated acta date.
            return findMostRecentDateInRole(history, MessageRole.USER).or(() -> findMostRecentDate(history));
        }
        return findMostRecentDate(history);
    }

    /** True when history mentions more than one distinct acta date (metadata or text). */
    static boolean hasMultipleDistinctAnchorDates(List<ConversationMemoryTurn> history) {
        return countDistinctAnchorDates(history) > 1;
    }

    static int countDistinctAnchorDates(List<ConversationMemoryTurn> history) {
        if (history == null || history.isEmpty()) {
            return 0;
        }
        Set<String> distinct = collectDistinctAnchorDates(history);
        return distinct.size();
    }

    static boolean requiresUniqueAnchorDate(String lowerQuery) {
        return isActaStructuredFieldFollowUp(lowerQuery)
                && !lowerQuery.contains("esa reunión")
                && !lowerQuery.contains("ese acta")
                && !lowerQuery.contains("esa acta")
                && !lowerQuery.contains("esa fecha");
    }

    static boolean isActaStructuredFieldFollowUp(String lower) {
        if (lower == null || lower.isBlank() || isCorpusWideAggregate(lower)) {
            return false;
        }
        if (lower.contains("presidente") || lower.contains("presidenta")) {
            return true;
        }
        if (lower.contains("secretari")) {
            return true;
        }
        if (mentionsParticipants(lower)) {
            return true;
        }
        if (isTimeFieldFollowUp(lower)) {
            return true;
        }
        if (lower.contains("duración") || lower.contains("duracion") || lower.contains("cuánto duró") || lower.contains("cuanto duro")) {
            return true;
        }
        if (lower.contains("lugar")
                || ((lower.contains("dónde") || lower.contains("donde"))
                        && (lower.contains("acta") || lower.contains("reunión") || lower.contains("reunion")))) {
            return true;
        }
        if (lower.contains("temas")
                || lower.contains("tema tratado")
                || lower.contains("temas tratados")
                || lower.contains("orden del día")
                || lower.contains("orden del dia")) {
            return true;
        }
        if (lower.contains("acuerdos") || lower.contains("acuerdo")) {
            return true;
        }
        return false;
    }

    static boolean isActaRoleOrTimeFollowUp(String lower) {
        return isActaStructuredFieldFollowUp(lower);
    }

    private static boolean isTimeFieldFollowUp(String lower) {
        if (lower.contains("hora de inicio") || lower.contains("hora de fin") || lower.contains("hora de finalización") || lower.contains("hora de finalizacion")) {
            return true;
        }
        if (!lower.contains("hora")) {
            return false;
        }
        return lower.contains("empez")
                || lower.contains("termin")
                || lower.contains("comenz")
                || lower.contains("finaliz")
                || lower.contains("inicio")
                || lower.contains(" fin");
    }

    private static boolean isCorpusWideAggregate(String lower) {
        return lower.contains("todas las actas")
                || lower.contains("cada acta")
                || lower.contains("cuántas actas")
                || lower.contains("cuantas actas")
                || lower.contains("hay actas")
                || lower.contains("todas las reuniones")
                || lower.contains("cuántas reuniones")
                || lower.contains("cuantas reuniones");
    }

    static Optional<String> findUniqueAnchorDate(List<ConversationMemoryTurn> history) {
        Set<String> distinct = collectDistinctAnchorDates(history);
        if (distinct.size() == 1) {
            return Optional.of(distinct.iterator().next());
        }
        return Optional.empty();
    }

    static Optional<String> findMostRecentStructuredAnchor(List<ConversationMemoryTurn> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            ConversationMemoryTurn turn = history.get(i);
            if (turn == null || turn.role() != MessageRole.ASSISTANT) {
                continue;
            }
            Optional<String> fromMeta = anchoredDateFromMetadata(turn.executionMetadata());
            if (fromMeta.isPresent()) {
                return fromMeta;
            }
        }
        return Optional.empty();
    }

    private static Set<String> collectDistinctAnchorDates(List<ConversationMemoryTurn> history) {
        Set<String> structured = collectStructuredAnchorDates(history);
        if (!structured.isEmpty()) {
            return structured;
        }
        Set<String> distinct = new LinkedHashSet<>();
        for (int i = history.size() - 1; i >= 0; i--) {
            ConversationMemoryTurn turn = history.get(i);
            if (turn == null) {
                continue;
            }
            firstDateInText(turn.content()).ifPresent(distinct::add);
        }
        return distinct;
    }

    /** Visible for runtime tests — most recent user turn carrying an explicit date. */
    static Optional<String> findMostRecentUserStatedDate(List<ConversationMemoryTurn> history) {
        return findMostRecentDateInRole(history, MessageRole.USER);
    }

    private static Set<String> collectStructuredAnchorDates(List<ConversationMemoryTurn> history) {
        Set<String> distinct = new LinkedHashSet<>();
        for (int i = history.size() - 1; i >= 0; i--) {
            ConversationMemoryTurn turn = history.get(i);
            if (turn == null || turn.role() != MessageRole.ASSISTANT) {
                continue;
            }
            anchoredDateFromMetadata(turn.executionMetadata()).ifPresent(distinct::add);
        }
        return distinct;
    }

    static Optional<String> firstExplicitDateInText(String text) {
        return firstDateInText(text != null ? text : "");
    }

    private static Optional<String> anchoredDateFromMetadata(Map<String, Object> executionMetadata) {
        Optional<String> anchored = ConversationMemoryAnchorMetadata.readAnchoredActaDate(executionMetadata);
        if (anchored.isPresent()) {
            return anchored;
        }
        return ConversationMemoryAnchorMetadata.readLastReferencedDate(executionMetadata);
    }

    private static boolean needsExpansion(String lower) {
        if (containsDate(lower)) {
            return false;
        }
        return lower.contains("esa reunión")
                || lower.contains("ese acta")
                || lower.contains("esa acta")
                || lower.contains("esa fecha")
                || lower.contains("ellos")
                || (lower.contains("los participantes") && !containsDate(lower))
                || isActaStructuredFieldFollowUp(lower);
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
        Optional<String> structured = findMostRecentStructuredAnchor(history);
        if (structured.isPresent()) {
            return structured;
        }
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

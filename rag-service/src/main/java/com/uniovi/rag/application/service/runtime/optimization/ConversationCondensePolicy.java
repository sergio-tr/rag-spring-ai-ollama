package com.uniovi.rag.application.service.runtime.optimization;

import java.util.Locale;
import java.util.regex.Pattern;

/** Cheap deterministic checks before LLM conversation condense. */
public final class ConversationCondensePolicy {

    private static final Pattern FOLLOW_UP_REFERENCE =
            Pattern.compile(
                    "(?i)\\b(?:eso|esa|ese|aquella?|ello|dicho tema|la anterior|turno anterior|misma reunión|misma reunion|en esa|en ese|lo anterior|lo mismo)\\b",
                    Pattern.UNICODE_CASE);
    private static final Pattern SELF_CONTAINED_ACTA =
            Pattern.compile(
                    "(?i)(?:en qué acta|en que acta|dime qué actas|dime que actas|a qué actas|a que actas|quién fue|quien fue|cuántas actas|cuantas actas|dime los lugares)",
                    Pattern.UNICODE_CASE);

    private ConversationCondensePolicy() {}

    public static boolean isSelfContainedQuestion(String latestUserTurn) {
        if (latestUserTurn == null || latestUserTurn.isBlank()) {
            return true;
        }
        String t = latestUserTurn.trim();
        if (t.length() >= 40 && SELF_CONTAINED_ACTA.matcher(t).find()) {
            return true;
        }
        return !requiresMemoryReference(t) && t.length() >= 25;
    }

    public static boolean requiresMemoryReference(String latestUserTurn) {
        if (latestUserTurn == null || latestUserTurn.isBlank()) {
            return false;
        }
        return FOLLOW_UP_REFERENCE.matcher(latestUserTurn).find();
    }
}

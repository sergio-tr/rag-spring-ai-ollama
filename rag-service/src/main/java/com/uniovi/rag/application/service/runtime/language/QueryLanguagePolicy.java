package com.uniovi.rag.application.service.runtime.language;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Minimal query-language detection for answer/abstention copy and metadata tooling (no translation pipeline).
 */
public final class QueryLanguagePolicy {

    public enum DetectedLanguage {
        SPANISH,
        ENGLISH,
        OTHER
    }

    private static final Pattern ENGLISH_HINT =
            Pattern.compile(
                    "\\b(the|what|when|where|which|who|how|was|were|meeting|meetings|minutes|minute|president|secretary|agenda|attendees|date|document|elevator|lift|mention|mentioned|discuss|discussed)\\b",
                    Pattern.CASE_INSENSITIVE);

    private QueryLanguagePolicy() {}

    public static DetectedLanguage detect(String query) {
        if (query == null || query.isBlank()) {
            return DetectedLanguage.OTHER;
        }
        if (looksSpanish(query)) {
            return DetectedLanguage.SPANISH;
        }
        if (looksEnglish(query)) {
            return DetectedLanguage.ENGLISH;
        }
        return DetectedLanguage.OTHER;
    }

    public static boolean looksSpanish(String query) {
        if (query == null) {
            return false;
        }
        String q = query.toLowerCase(Locale.ROOT);
        return q.contains("¿")
                || q.contains("¡")
                || q.contains("acta")
                || q.contains("reunión")
                || q.contains("reunion")
                || q.contains("documento")
                || q.contains("asistente")
                || q.contains("presidente")
                || q.contains("duración")
                || q.contains("duracion");
    }

    public static boolean looksEnglish(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String q = query.toLowerCase(Locale.ROOT);
        return ENGLISH_HINT.matcher(q).find();
    }

    /** BCP-47-ish tag stored on document metadata for retrieval/diagnostics. */
    public static String documentLanguageTag(String content, String filename) {
        DetectedLanguage fromContent = detect(content != null ? content.substring(0, Math.min(content.length(), 500)) : "");
        if (fromContent == DetectedLanguage.SPANISH) {
            return "es";
        }
        if (fromContent == DetectedLanguage.ENGLISH) {
            return "en";
        }
        if (filename != null) {
            String lower = filename.toLowerCase(Locale.ROOT);
            if (lower.contains("acta") || lower.contains("reunion")) {
                return "es";
            }
        }
        return "und";
    }

    public static String answerInQueryLanguageInstruction(DetectedLanguage language) {
        return switch (language) {
            case SPANISH -> "Responde en el mismo idioma que la pregunta del usuario (español).";
            case ENGLISH -> "Answer in the same language as the user's question (English).";
            case OTHER -> "Answer in the same language as the user's question.";
        };
    }
}

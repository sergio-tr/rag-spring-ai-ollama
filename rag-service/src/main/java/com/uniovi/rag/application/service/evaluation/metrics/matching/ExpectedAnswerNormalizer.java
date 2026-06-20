package com.uniovi.rag.application.service.evaluation.metrics.matching;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Evaluation-only answer normalization beyond the legacy raw matcher. */
public final class ExpectedAnswerNormalizer {

    private static final Set<String> LEADING_ARTICLES =
            Set.of("el", "la", "los", "las", "un", "una", "unos", "unas");

    private ExpectedAnswerNormalizer() {}

    public static String rawNormalize(String text) {
        return normalizeLegacy(text);
    }

    /** Delegates to the existing MVP raw normalization (NFKC + lowercase + whitespace). */
    public static String normalizeLegacy(String text) {
        if (text == null) {
            return "";
        }
        String n = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        return n.replaceAll("\\s+", " ").trim();
    }

    /** Accent-folded normalization with punctuation softened for substring checks. */
    public static String normalizedFold(String text) {
        if (text == null) {
            return "";
        }
        String nfd = Normalizer.normalize(text, Normalizer.Form.NFD);
        String stripped = nfd.replaceAll("\\p{M}+", "");
        String lower = Normalizer.normalize(stripped, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        String punct = lower.replaceAll("[.,;:!?¿¡()\\[\\]«»]", " ");
        return punct.replaceAll("\\s+", " ").trim();
    }

    public static boolean normalizedContains(String expected, String actual) {
        String ne = normalizedFold(expected);
        String na = normalizedFold(actual);
        if (ne.isEmpty() || na.isEmpty()) {
            return false;
        }
        return na.contains(ne);
    }

    public static Set<String> normalizedTokens(String text, TokenMode mode) {
        String folded = normalizedFold(text);
        if (folded.isBlank()) {
            return Set.of();
        }
        Set<String> tokens =
                Arrays.stream(folded.split("\\s+"))
                        .map(String::trim)
                        .filter(t -> !t.isBlank())
                        .filter(t -> t.length() >= mode.minTokenLength())
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        if (mode == TokenMode.ENTITY) {
            return tokens.stream()
                    .filter(t -> !LEADING_ARTICLES.contains(t))
                    .filter(t -> !ENTITY_STOPWORDS.contains(t))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        return tokens;
    }

    private static final Set<String> ENTITY_STOPWORDS =
            Set.of(
                    "que",
                    "con",
                    "por",
                    "para",
                    "del",
                    "de",
                    "en",
                    "al",
                    "se",
                    "su",
                    "sus",
                    "the",
                    "and",
                    "for");

    public enum TokenMode {
        GENERAL(1),
        ENTITY(4);

        private final int minTokenLength;

        TokenMode(int minTokenLength) {
            this.minTokenLength = minTokenLength;
        }

        int minTokenLength() {
            return minTokenLength;
        }
    }
}

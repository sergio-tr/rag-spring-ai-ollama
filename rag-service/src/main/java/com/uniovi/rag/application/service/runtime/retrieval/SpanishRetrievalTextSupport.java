package com.uniovi.rag.application.service.runtime.retrieval;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

import java.util.Arrays;

/** Spanish-oriented normalization helpers for lexical sparse retrieval. */
public final class SpanishRetrievalTextSupport {

    private static final Set<String> STOPWORDS =
            Set.copyOf(
                    Arrays.asList(
                            "a", "al", "algo", "alguna", "algunas", "alguno", "algunos", "ante", "como", "con",
                            "cual", "cuales", "cuando", "cuanta", "cuantas", "cuantos", "de", "del", "dime", "donde",
                            "el", "en", "es", "esta", "estas", "este", "estos", "exactamente", "fue", "fueron",
                            "hablaron", "hablo", "ha", "han", "hubo", "indicame", "la", "las", "lo", "los", "me",
                            "mencionan", "menciona", "menciono", "mis", "muchas", "muchos", "mas", "ninguna",
                            "ninguno", "numero", "o", "para", "por", "que", "quien", "registradas", "registrados",
                            "reunion", "reuniones", "respecto", "se", "ser", "si", "sin", "sobre", "sus", "tal", "te",
                            "trato", "tu", "tus", "un", "una", "uno", "unos", "unas", "verifica", "y", "ano", "acta",
                            "actas", "asunto", "asuntos", "celebradas", "celebrada", "comento", "contexto", "cuanto",
                            "dias", "dia", "duracion", "indica", "participaron", "presididas", "presidida", "quedo",
                            "relacion", "veces"));

    private SpanishRetrievalTextSupport() {}

    public static String foldAccents(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}+", "");
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String collapsed = raw.trim().replaceAll("\\s+", " ");
        String stripped = collapsed.replaceAll("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$", "").trim();
        return foldAccents(stripped.toLowerCase(Locale.ROOT));
    }

    public static boolean isStopword(String token) {
        if (token == null || token.isBlank()) {
            return true;
        }
        String folded = foldAccents(token.toLowerCase(Locale.ROOT).trim());
        return STOPWORDS.contains(folded);
    }

    public static boolean isYearToken(String token) {
        return token != null && token.matches("\\d{4}");
    }

    public static boolean isSignificantToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String t = token.trim();
        if (isYearToken(t)) {
            return true;
        }
        if (t.length() < 3 && !t.matches("\\d+")) {
            return false;
        }
        return !isStopword(t);
    }
}

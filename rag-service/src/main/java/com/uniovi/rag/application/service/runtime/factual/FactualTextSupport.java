package com.uniovi.rag.application.service.runtime.factual;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

final class FactualTextSupport {

    private static final Pattern P_NO_HAY = Pattern.compile("\\bno\\s+hay\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern P_NO_EXISTE = Pattern.compile("\\bno\\s+existen?\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern P_NINGUNA = Pattern.compile("\\bninguna?\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern P_NO_SE_ENCUENTRA = Pattern.compile("\\bno\\s+se\\s+encuentra\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern P_NO_SE_ENCONTRADO =
            Pattern.compile("\\bno\\s+se\\s+encontr", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern P_NO_HE_ENCONTRADO =
            Pattern.compile("\\bno\\s+he\\s+encontrado\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern P_NO_SE_COMENTO =
            Pattern.compile("\\bno\\s+se\\s+coment", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern P_NO_SE_MENCIONA = Pattern.compile("\\bno\\s+se\\s+menciona\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern P_NO_CONSTA = Pattern.compile("\\bno\\s+consta\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern P_EXPLICIT_NO = Pattern.compile("^\\s*no\\s*,", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private FactualTextSupport() {}

    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String n = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    static boolean hasHighPrecisionNegativePhrasing(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = normalize(text);
        return P_NO_HAY.matcher(normalized).find()
                || P_NO_EXISTE.matcher(normalized).find()
                || P_NINGUNA.matcher(normalized).find()
                || P_NO_SE_ENCUENTRA.matcher(normalized).find()
                || P_NO_SE_ENCONTRADO.matcher(normalized).find()
                || P_NO_HE_ENCONTRADO.matcher(normalized).find()
                || P_NO_SE_COMENTO.matcher(normalized).find()
                || P_NO_SE_MENCIONA.matcher(normalized).find()
                || P_NO_CONSTA.matcher(normalized).find()
                || P_EXPLICIT_NO.matcher(normalized).find();
    }
}

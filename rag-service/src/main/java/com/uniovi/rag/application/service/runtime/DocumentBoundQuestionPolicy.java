package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.util.QueryDateSupport;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Heuristic intent classifier for questions that must be answered using project documents only.
 *
 * <p>This is intentionally deterministic and LLM-free: it is used as a safety gate to prevent
 * hallucinations when users ask about meeting minutes and project documents.
 */
final class DocumentBoundQuestionPolicy {

    private static final Pattern DATE_LIKE = QueryDateSupport.QUERY_DATE_SIGNAL;

    private DocumentBoundQuestionPolicy() {}

    static boolean isDocumentBoundQuestion(String rawUserText) {
        if (rawUserText == null) {
            return false;
        }
        String q = rawUserText.trim();
        if (q.isEmpty()) {
            return false;
        }
        String s = q.toLowerCase(Locale.ROOT);

        // Explicit references to the corpus/documents.
        if (containsAny(
                s,
                "acta",
                "actas",
                "reunion",
                "reunión",
                "reuniones",
                "minuta",
                "minutas",
                "documento",
                "documentos",
                "segun los documentos",
                "según los documentos",
                "en los documentos",
                "en las actas",
                "en las acta",
                "en las reuniones",
                "de las que tienes conocimiento",
                "en el proyecto",
                "del proyecto",
                "en este proyecto",
                "presupuesto",
                "presupuestos")) {
            return true;
        }

        // Common meeting-minutes facts (even if user does not mention "documents").
        if (containsAny(
                s,
                "asistentes",
                "asistente",
                "presidio",
                "presidió",
                "presidente",
                "vicepresidente",
                "vicepresident",
                "secretario",
                "secretaria",
                "duracion",
                "duración",
                "orden del dia",
                "orden del día",
                "acuerdo",
                "acuerdos",
                "decision",
                "decisión",
                "decisiones",
                "resumen del acta",
                "resumen de acta",
                "hazme un resumen",
                "haz un resumen",
                "cuantas actas",
                "cuántas actas",
                "mencionan",
                "menciona")) {
            return true;
        }

        // Date-like queries are almost always asking about a specific meeting.
        return DATE_LIKE.matcher(s).find();
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String k : keywords) {
            if (text.contains(k)) {
                return true;
            }
        }
        return false;
    }
}


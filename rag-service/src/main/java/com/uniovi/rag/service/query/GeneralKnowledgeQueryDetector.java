package com.uniovi.rag.service.query;

import java.util.regex.Pattern;

/**
 * Heuristic: route to direct LLM (no RAG) when the user clearly asks for general / conversational
 * content and does not mention meeting minutes / documents. Small models often ignore long RAG prompts
 * and reply with meta-requests for "context".
 */
public final class GeneralKnowledgeQueryDetector {

    /**
     * If these appear, the question likely refers to stored minutes — do not bypass RAG.
     */
    private static final Pattern DOCUMENT_SCOPE = Pattern.compile(
            "\\b(acta|actas|reuni[oó]n|minuta|minutas|orden del d[ií]a|documentos?|"
                    + "asistentes|asistencia|propuesta|acuerdos?|votaci[oó]n|"
                    + "comparecencia|convocatoria|aprobaci[oó]n)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * Obvious general / joke / small-talk intents (ES + EN).
     */
    private static final Pattern GENERAL_OR_CONVERSATIONAL = Pattern.compile(
            "\\b(chiste|chistes|broma|bromas|joke|jokes|hazme re[ií]r|hazme reir|"
                    + "cu[eé]ntame un chiste|cu[eé]ntame una broma|"
                    + "tell me a joke|make me laugh)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private GeneralKnowledgeQueryDetector() {
    }

    /**
     * @return true if the query should be answered without document retrieval.
     */
    public static boolean likelyGeneralKnowledgeOnly(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String q = query.trim();
        if (DOCUMENT_SCOPE.matcher(q).find()) {
            return false;
        }
        return GENERAL_OR_CONVERSATIONAL.matcher(q).find();
    }
}

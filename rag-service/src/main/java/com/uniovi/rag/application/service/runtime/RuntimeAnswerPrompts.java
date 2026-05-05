package com.uniovi.rag.application.service.runtime;

/**
 * User-turn prompt templates for runtime workflows (single LLM call path).
 */
public final class RuntimeAnswerPrompts {

    public static final String INSUFFICIENT_DOCUMENT_CONTEXT_MESSAGE_ES =
            "No he encontrado información suficiente en los documentos del proyecto para responder con seguridad.";

    public static final String INSUFFICIENT_DOCUMENT_CONTEXT_MESSAGE_EN =
            "I could not find enough information in the project documents to answer confidently.";

    public static final String DOCUMENT_BOUND_REQUIRES_RETRIEVAL_MESSAGE_ES =
            "Para responder a esa pregunta necesito usar los documentos del proyecto, pero esta conversación no tiene retrieval activo o no hay documentos disponibles.";

    public static final String DOCUMENT_BOUND_REQUIRES_RETRIEVAL_MESSAGE_EN =
            "To answer that question I need to use the project documents, but retrieval is not active or no documents are available for this chat.";

    private static final String GENERAL_TEMPLATE =
            """
            You are a helpful assistant. Retrieved fragments from a meeting-minutes database are provided below when available.

            PRIORITY — answer the user's question:
            - If the question is general (jokes, definitions, chat, etc.) you may answer directly using general knowledge in the user's language.
            - If the question is about the documents, base factual claims on the context only; never invent acta-specific facts not in the context.

            RULES for document-specific answers:
            1. NEVER invent names, dates, places, actas, or facts not in the context when answering about meetings.
            2. Answer in the SAME LANGUAGE as the user's question.
            3. Be concise. Do not add unnecessary headers.

            <Question> %s </Question>
            <Context> %s </Context>

            Provide your direct answer now (in the same language as the question):
            """;

    private static final String DOCUMENT_BOUND_TEMPLATE =
            """
            You are a helpful assistant. The user is asking a question that MUST be answered using ONLY the provided document context.

            CRITICAL RULES:
            1. You MUST NOT use general world knowledge. Use ONLY the context below.
            2. If the context is empty or does not contain enough evidence to answer, reply with EXACTLY this sentence (and nothing else):
               "%s"
            3. Do not invent names, dates, counts, presidents, assistants, or meeting facts not present in the context.
            4. Answer in the SAME LANGUAGE as the user's question.

            <Question> %s </Question>
            <Context> %s </Context>

            Answer now:
            """;

    private RuntimeAnswerPrompts() {
    }

    public static String ragUserTurn(String rawQuestion, String contextBlock) {
        return ragUserTurn(rawQuestion, contextBlock, false);
    }

    public static String ragUserTurn(String rawQuestion, String contextBlock, boolean documentBound) {
        String q = rawQuestion != null ? rawQuestion : "";
        String c = contextBlock != null ? contextBlock : "";
        if (documentBound) {
            String abstain = insufficientDocumentContextMessageFor(q);
            return String.format(DOCUMENT_BOUND_TEMPLATE, abstain, q, c);
        }
        return String.format(GENERAL_TEMPLATE, q, c);
    }

    public static boolean requiresStrictDocumentGrounding(String rawQuestion) {
        return DocumentBoundQuestionPolicy.isDocumentBoundQuestion(rawQuestion);
    }

    public static String insufficientDocumentContextMessageFor(String rawQuestion) {
        if (looksSpanish(rawQuestion)) {
            return INSUFFICIENT_DOCUMENT_CONTEXT_MESSAGE_ES;
        }
        return INSUFFICIENT_DOCUMENT_CONTEXT_MESSAGE_EN;
    }

    public static String documentBoundRequiresRetrievalMessageFor(String rawQuestion) {
        if (looksSpanish(rawQuestion)) {
            return DOCUMENT_BOUND_REQUIRES_RETRIEVAL_MESSAGE_ES;
        }
        return DOCUMENT_BOUND_REQUIRES_RETRIEVAL_MESSAGE_EN;
    }

    private static boolean looksSpanish(String rawQuestion) {
        if (rawQuestion == null) {
            return true;
        }
        String q = rawQuestion.toLowerCase();
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
}

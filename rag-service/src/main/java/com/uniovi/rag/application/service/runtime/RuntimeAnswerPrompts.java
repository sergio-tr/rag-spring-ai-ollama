package com.uniovi.rag.application.service.runtime;

/**
 * User-turn prompt templates for runtime workflows (single LLM call path).
 */
public final class RuntimeAnswerPrompts {

    private static final String RAG_TEMPLATE =
            """
            You are a helpful assistant. Retrieved fragments from a meeting-minutes database are provided below when available.

            PRIORITY — answer the user's question:
            - If the question is general (jokes, definitions, chat, etc.) OR the context does not help answer it, answer directly using general knowledge in the user's language. Do not ask for more context.
            - If the question is about the documents AND the context is relevant, base factual claims on the context only; never invent acta-specific facts not in the context.

            RULES for document-specific answers:
            1. NEVER invent names, dates, places, actas, or facts not in the context when answering about meetings.
            2. Answer in the SAME LANGUAGE as the user's question.
            3. Be concise. Do not add unnecessary headers.

            <Question> %s </Question>
            <Context> %s </Context>

            Provide your direct answer now (in the same language as the question):
            """;

    private RuntimeAnswerPrompts() {
    }

    public static String ragUserTurn(String rawQuestion, String contextBlock) {
        String q = rawQuestion != null ? rawQuestion : "";
        String c = contextBlock != null ? contextBlock : "";
        return String.format(RAG_TEMPLATE, q, c);
    }
}

package com.uniovi.rag.application.service.knowledge;

/**
 * Detects Ollama / embedding provider errors caused by input exceeding model context limits.
 */
public final class EmbeddingContextLimitFailures {

    private EmbeddingContextLimitFailures() {}

    public static boolean isContextLimitFailure(Throwable t) {
        Throwable cur = t;
        int depth = 0;
        while (cur != null && depth++ < 32) {
            String msg = cur.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase();
                if (lower.contains("input length exceeds the context length")
                        || lower.contains("exceeds the context length")
                        || lower.contains("context length")
                        || (lower.contains("token") && lower.contains("limit"))
                        || (lower.contains("maximum context") && lower.contains("exceeded"))) {
                    return true;
                }
            }
            cur = cur.getCause();
        }
        return false;
    }
}

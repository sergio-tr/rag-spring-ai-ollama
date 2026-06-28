package com.uniovi.rag.application.service.knowledge;

import com.uniovi.rag.configuration.RagIndexingEmbeddingProperties;
import org.springframework.stereotype.Component;

/**
 * Truncates and caps text before {@link org.springframework.ai.embedding.EmbeddingModel#embed} during indexing.
 */
@Component
public class IndexingEmbeddingGuard {

    private final RagIndexingEmbeddingProperties properties;

    public IndexingEmbeddingGuard(RagIndexingEmbeddingProperties properties) {
        this.properties = properties;
    }

    public int effectiveEmbedMaxChars(int profileChunkMaxChars) {
        return properties.effectiveEmbedMaxChars(profileChunkMaxChars);
    }

    public boolean retryOnContextLength() {
        return properties.retryOnContextLength();
    }

    public boolean isContextLengthFailure(Throwable t) {
        return EmbeddingContextLimitFailures.isContextLimitFailure(t);
    }

    /**
     * @param text raw chunk or document text
     * @param maxChars hard ceiling for this embed input
     */
    public SafeEmbedText prepareForEmbedding(String text, int maxChars) {
        if (text == null || text.isEmpty()) {
            return new SafeEmbedText("", false);
        }
        String trimmed = text.trim();
        if (trimmed.length() <= maxChars) {
            return new SafeEmbedText(trimmed, false);
        }
        int end = computeTruncationEnd(trimmed, maxChars);
        String truncated = trimmed.substring(0, end).trim();
        if (truncated.isEmpty()) {
            truncated = trimmed.substring(0, maxChars);
        }
        return new SafeEmbedText(truncated, true);
    }

    private static int computeTruncationEnd(String text, int maxChars) {
        int end = Math.min(maxChars, text.length());
        if (end >= text.length()) {
            return end;
        }
        int lastBreak = end;
        int searchFrom = Math.max(0, end - (maxChars / 4));
        for (int i = end - 1; i >= searchFrom; i--) {
            char c = text.charAt(i);
            if (c == '\n' || c == '.' || c == '!' || c == '?' || c == ' ') {
                lastBreak = i + 1;
                break;
            }
        }
        return lastBreak;
    }

    public record SafeEmbedText(String text, boolean truncated) {}
}

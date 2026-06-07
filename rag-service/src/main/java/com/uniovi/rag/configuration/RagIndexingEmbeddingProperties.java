package com.uniovi.rag.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Hard limits for text sent to {@link org.springframework.ai.embedding.EmbeddingModel} during knowledge indexing.
 * Dense vectors must never exceed the embedder context; lexical/BM25 may use fuller text stored separately.
 */
@ConfigurationProperties(prefix = "rag.indexing.embedding")
public record RagIndexingEmbeddingProperties(
        /** Maximum characters allowed in a single embed call (model context ceiling). */
        @DefaultValue("2048") int maxInputChars,
        /** Upper bound for profile/index chunk size when splitting before embed. */
        @DefaultValue("400") int maxChunkChars,
        /** When true, halve chunk size and retry after Ollama context-length failures. */
        @DefaultValue("true") boolean retryOnContextLength,
        /**
         * Fraction of {@link #maxInputChars()} applied as a safety cap on each chunk
         * (accounts for tokenizer overhead vs raw char count).
         */
        @DefaultValue("0.85") double contextLengthSafetyRatio) {

    public RagIndexingEmbeddingProperties {
        if (maxInputChars <= 0) {
            throw new IllegalArgumentException("rag.indexing.embedding.max-input-chars must be > 0");
        }
        if (maxChunkChars <= 0) {
            throw new IllegalArgumentException("rag.indexing.embedding.max-chunk-chars must be > 0");
        }
        if (contextLengthSafetyRatio <= 0 || contextLengthSafetyRatio > 1.0) {
            throw new IllegalArgumentException(
                    "rag.indexing.embedding.context-length-safety-ratio must be in (0, 1]");
        }
    }

    /** Effective per-chunk char cap for embedding after profile chunk size and safety ratio. */
    public int effectiveEmbedMaxChars(int profileChunkMaxChars) {
        int profileCap = profileChunkMaxChars > 0 ? profileChunkMaxChars : maxChunkChars;
        int cappedProfile = Math.min(profileCap, maxChunkChars);
        int safetyCap = (int) Math.floor(maxInputChars * contextLengthSafetyRatio);
        return Math.max(64, Math.min(cappedProfile, safetyCap));
    }
}

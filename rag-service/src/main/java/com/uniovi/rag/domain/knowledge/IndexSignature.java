package com.uniovi.rag.domain.knowledge;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Canonical index fingerprint for embeddings + chunk layout. Hash order is fixed for golden tests.
 *
 * @param embeddingModelId   embedding model identifier (e.g. Ollama model name)
 * @param chunkMaxChars      chunking policy size
 * @param normalizationVersion normalizer revision
 * @param indexLayoutMode    reserved; {@code CHUNK} in current delivery
 */
public record IndexSignature(
        String embeddingModelId,
        int chunkMaxChars,
        String normalizationVersion,
        String indexLayoutMode
) {

    public static final String DEFAULT_NORMALIZATION_VERSION = "1";
    public static final String LAYOUT_CHUNK = "CHUNK";

    public static IndexSignature chunkDefaults(String embeddingModelId, int chunkMaxChars) {
        return new IndexSignature(
                embeddingModelId,
                chunkMaxChars,
                DEFAULT_NORMALIZATION_VERSION,
                LAYOUT_CHUNK);
    }

    /**
     * Stable SHA-256 hex over ordered fields (pipe-separated).
     */
    public String toHashHex() {
        String canonical = String.join("|",
                embeddingModelId != null ? embeddingModelId : "",
                Integer.toString(chunkMaxChars),
                normalizationVersion != null ? normalizationVersion : "",
                indexLayoutMode != null ? indexLayoutMode : "");
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

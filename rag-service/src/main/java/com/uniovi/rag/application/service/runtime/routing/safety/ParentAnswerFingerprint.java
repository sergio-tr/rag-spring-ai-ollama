package com.uniovi.rag.application.service.runtime.routing.safety;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Deterministic SHA-256 hex fingerprint for matcher-visible parent answer text. */
public final class ParentAnswerFingerprint {

    private ParentAnswerFingerprint() {}

    public static String sha256Hex(String text) {
        String normalized = text != null ? text : "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

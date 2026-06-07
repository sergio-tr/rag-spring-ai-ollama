package com.uniovi.rag.infrastructure.observability;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;

/**
 * Default-deny redaction for span/log attributes: no raw user queries, prompts, documents, or secrets.
 */
public final class TelemetryRedaction {

    private static final Set<String> FORBIDDEN_KEYS = Set.of(
            "query",
            "prompt",
            "content",
            "body",
            "answer",
            "document",
            "email",
            "password",
            "token",
            "authorization",
            "user_text",
            "userText");

    private TelemetryRedaction() {}

    public static boolean isForbiddenKey(String key) {
        if (key == null || key.isBlank()) {
            return true;
        }
        String normalized = key.toLowerCase(Locale.ROOT).replace('-', '_');
        return FORBIDDEN_KEYS.contains(normalized);
    }

    /**
     * Returns a copy of attributes with forbidden keys replaced by safe derivatives where possible.
     */
    public static Map<String, String> safeAttributes(Map<String, String> input) {
        if (input == null || input.isEmpty()) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : input.entrySet()) {
            String key = e.getKey();
            String value = e.getValue();
            if (isForbiddenKey(key)) {
                if ("query".equalsIgnoreCase(key) || "content".equalsIgnoreCase(key)) {
                    out.put("queryLength", String.valueOf(length(value)));
                }
                continue;
            }
            out.put(key, value != null ? value : "");
        }
        return Map.copyOf(out);
    }

    public static String queryLength(String text) {
        return String.valueOf(length(text));
    }

    public static String optionalHashPrefix(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }

    private static int length(String text) {
        return text == null ? 0 : text.length();
    }
}

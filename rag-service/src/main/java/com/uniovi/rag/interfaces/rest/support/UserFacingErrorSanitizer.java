package com.uniovi.rag.interfaces.rest.support;

/**
 * Reduces sensitive / unreadable noise (HTML proxies, stack traces) before exposing strings on APIs or async task payloads.
 */
public final class UserFacingErrorSanitizer {

    private UserFacingErrorSanitizer() {}

    public static String sanitize(String raw, int maxLen) {
        if (raw == null) {
            return "";
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return "";
        }
        String lower = t.toLowerCase();
        if (looksLikeHtml(lower)) {
            return "";
        }
        if (looksLikeStackTrace(t)) {
            return "";
        }
        return truncate(t, maxLen);
    }

    public static String sanitizeOrDefault(String raw, int maxLen, String fallback) {
        String s = sanitize(raw, maxLen);
        return s.isEmpty() ? fallback : s;
    }

    private static boolean looksLikeHtml(String lowerTrimmed) {
        return lowerTrimmed.startsWith("<!doctype html")
                || lowerTrimmed.startsWith("<html")
                || (lowerTrimmed.contains("<html") && lowerTrimmed.contains("</html"))
                || (lowerTrimmed.contains("<body") && lowerTrimmed.contains("</body"));
    }

    private static boolean looksLikeStackTrace(String t) {
        var frames = t.split("\n", -1);
        int atLines = 0;
        for (String line : frames) {
            if (line.trim().startsWith("at ") || line.contains("\tat ")) {
                atLines++;
            }
        }
        return atLines >= 2;
    }

    private static String truncate(String t, int maxLen) {
        if (maxLen <= 0 || t.length() <= maxLen) {
            return t;
        }
        return t.substring(0, maxLen) + "…";
    }
}

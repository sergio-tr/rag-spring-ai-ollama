package com.uniovi.rag.application.service.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Shared rules for matching allowlisted or requested Ollama model ids against {@code /api/tags} names.
 */
public final class OllamaInstalledModelMatcher {

    private OllamaInstalledModelMatcher() {}

    public static boolean matchesInstalledName(String allowlistedName, Set<String> installed) {
        if (allowlistedName == null || allowlistedName.isBlank() || installed == null || installed.isEmpty()) {
            return false;
        }
        String raw = allowlistedName.trim();
        if (installed.contains(raw)) {
            return true;
        }
        // Ollama commonly returns ":latest" tags; allow admin to store base name without explicit tag.
        if (!raw.contains(":")) {
            String withLatest = raw + ":latest";
            if (installed.contains(withLatest)) {
                return true;
            }
        }
        // Case-insensitive fallback (Ollama tags are typically lower-case; allowlist may differ).
        String rawLower = raw.toLowerCase(Locale.ROOT);
        for (String s : installed) {
            if (s == null) {
                continue;
            }
            if (s.trim().toLowerCase(Locale.ROOT).equals(rawLower)) {
                return true;
            }
            if (!raw.contains(":") && s.trim().toLowerCase(Locale.ROOT).equals(rawLower + ":latest")) {
                return true;
            }
        }
        return false;
    }

    /** Installed tag names that correspond to a requested model id (for admin check UI). */
    public static List<String> findMatchingInstalledNames(String requested, Set<String> installed) {
        if (requested == null || requested.isBlank() || installed == null || installed.isEmpty()) {
            return List.of();
        }
        String r = requested.trim();
        String rLower = r.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String name : installed) {
            if (name == null) {
                continue;
            }
            String n = name.trim();
            if (n.equals(r) || n.equalsIgnoreCase(r)) {
                out.add(n);
            } else if (!r.contains(":") && n.toLowerCase(Locale.ROOT).equals(rLower + ":latest")) {
                out.add(n);
            }
        }
        out.sort(Comparator.naturalOrder());
        return out;
    }

    public static String pickBestInstalledName(String requested, List<String> matches) {
        if (matches == null || matches.isEmpty()) {
            return requested != null ? requested.trim() : "";
        }
        for (String m : matches) {
            if (m != null && requested != null && m.equals(requested.trim())) {
                return m;
            }
        }
        return matches.getFirst();
    }
}

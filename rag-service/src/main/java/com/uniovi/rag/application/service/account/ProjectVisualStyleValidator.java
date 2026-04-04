package com.uniovi.rag.application.service.account;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

/**
 * Validates {@code colorHex} and catalog {@code iconKey} for project identity (plan P-DC-06).
 */
public final class ProjectVisualStyleValidator {

    private static final Set<String> ALLOWED_ICON_KEYS =
            Set.of("folder", "briefcase", "star", "code", "book", "chat", "lab", "rocket", "shield");

    private ProjectVisualStyleValidator() {
    }

    public static void validateColorHexOrNull(String colorHex) {
        if (colorHex == null || colorHex.isBlank()) {
            return;
        }
        if (!colorHex.matches("^#([0-9A-Fa-f]{6})$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "colorHex must match ^#([0-9A-Fa-f]{6})$");
        }
    }

    public static void validateIconKeyOrNull(String iconKey) {
        if (iconKey == null || iconKey.isBlank()) {
            return;
        }
        String k = iconKey.trim();
        if (k.length() > 64 || !ALLOWED_ICON_KEYS.contains(k)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "iconKey must be a known catalog value");
        }
    }
}

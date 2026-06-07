package com.uniovi.rag.domain.evaluation.workbook;

import java.util.Locale;
import java.util.Optional;

public enum DifficultyLevel {
    LOW,
    MEDIUM,
    HIGH;

    public static Optional<DifficultyLevel> tryParse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}

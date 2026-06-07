package com.uniovi.rag.domain.evaluation.workbook;

import java.util.Locale;
import java.util.Optional;

/** Protocol preset identifiers P0–P14. */
public enum RagExperimentalPresetCode {
    P0,
    P1,
    P2,
    P3,
    P4,
    P5,
    P6,
    P7,
    P8,
    P9,
    P10,
    P11,
    P12,
    P13,
    P14;

    public static Optional<RagExperimentalPresetCode> tryParse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String t = raw.trim().toUpperCase(Locale.ROOT);
        try {
            return Optional.of(valueOf(t));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}

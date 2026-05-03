package com.uniovi.rag.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class DateParsingSupport {

    private DateParsingSupport() {
    }

    public static LocalDate parseDateToLocalDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }

        // Normalize to lowercase to handle case variations (e.g., "Agosto" vs "agosto")
        String v = dateStr.trim().toLowerCase();

        // Try ISO format first (most common after normalization)
        try {
            return LocalDate.parse(v, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ignored) {
            // Not ISO after lowercasing; continue with locale-specific formatters below.
        }

        List<DateTimeFormatter> formatters =
                Arrays.asList(
                        DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es")),
                        DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es")),
                        // Spanish formats without quotes
                        DateTimeFormatter.ofPattern("d de MMMM de yyyy", Locale.forLanguageTag("es")),
                        DateTimeFormatter.ofPattern("dd de MMMM de yyyy", Locale.forLanguageTag("es")),
                        // Abbreviated month names
                        DateTimeFormatter.ofPattern("d 'de' MMM 'de' yyyy", Locale.forLanguageTag("es")),
                        DateTimeFormatter.ofPattern("dd 'de' MMM 'de' yyyy", Locale.forLanguageTag("es")),
                        DateTimeFormatter.ofPattern("d de MMM de yyyy", Locale.forLanguageTag("es")),
                        DateTimeFormatter.ofPattern("dd de MMM de yyyy", Locale.forLanguageTag("es")),
                        // Without "de" between day and month
                        DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("es")),
                        DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.forLanguageTag("es")),
                        // Numeric formats
                        DateTimeFormatter.ofPattern("d/M/yyyy"),
                        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                        DateTimeFormatter.ofPattern("d-M-yyyy"),
                        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
                        DateTimeFormatter.ofPattern("yyyy/MM/dd"),
                        DateTimeFormatter.ofPattern("yyyy.MM.dd"),
                        // With day of the week
                        DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es")),
                        DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH));

        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDate.parse(v, formatter);
            } catch (DateTimeParseException ignored) {
                // Try next formatter
            }
        }

        return null;
    }
}


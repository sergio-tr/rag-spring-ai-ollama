package com.uniovi.rag.util;

import com.uniovi.rag.application.service.runtime.query.ActaSlashDateSupport;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Canonical date detection, extraction, and parsing for Spanish meeting-minute queries.
 *
 * <p>Centralizes {@code de} vs {@code del} before the year, numeric formats (ISO, slash, dash, dot),
 * and shared regex fragments so metadata tools, retrieval, guards, and grounding stay aligned.
 */
public final class QueryDateSupport {

    private static final int UNICODE = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    /** Regex fragment: {@code 25 de febrero de 2025} / {@code 25 de febrero del 2025}. */
    public static final String LONG_DATE_REGEX_FRAGMENT =
            "\\b\\d{1,2}\\s+de\\s+\\p{L}+\\s+de[l]?\\s+\\d{4}\\b";

    /** Captures a full long-date phrase inside free text. */
    public static final Pattern LONG_DATE_PHRASE =
            Pattern.compile("\\b(\\d{1,2}\\s+de\\s+\\p{L}+\\s+de[l]?\\s+\\d{4})\\b", UNICODE);

    /** Parses a trimmed long-date candidate (grouped day / month name / year). */
    public static final Pattern LONG_DATE_PARSE =
            Pattern.compile("(\\d{1,2})\\s+de\\s+(\\p{L}+)\\s+de[l]?\\s+(\\d{4})", UNICODE);

    public static final Pattern ISO_DATE = Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2})\\b");

    public static final Pattern DMY_SLASH_OR_DASH_CAPTURE =
            Pattern.compile("\\b(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})\\b");

    /** @deprecated use {@link #DMY_SLASH_OR_DASH_CAPTURE} */
    @Deprecated
    public static final Pattern DMY_SLASH_CAPTURE = DMY_SLASH_OR_DASH_CAPTURE;

    /** @deprecated use {@link #DMY_SLASH_OR_DASH_CAPTURE} */
    @Deprecated
    public static final Pattern DMY_DASH_CAPTURE = DMY_SLASH_OR_DASH_CAPTURE;

    public static final Pattern DMY_DOT_CAPTURE = Pattern.compile("\\b(\\d{1,2}\\.\\d{1,2}\\.\\d{4})\\b");

    public static final Pattern SPANISH_DAY_MONTH_YEAR_NO_DE =
            Pattern.compile(
                    "\\b(\\d{1,2}\\s+(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre)\\s+\\d{4})\\b",
                    UNICODE);

    public static final Pattern YEAR_ANCHOR =
            Pattern.compile("\\b(?:año|ano)\\s+(?:del\\s+)?(\\d{4})\\b", UNICODE);

    public static final Pattern DEL_YEAR_ANCHOR = Pattern.compile("\\bdel\\s+año\\s+(\\d{4})\\b", UNICODE);

    public static final Pattern IN_YEAR = Pattern.compile("\\ben\\s+(20\\d{2})\\b", UNICODE);

    /** Slash/dash numeric dates or long Spanish dates (for answer synthesis, sparse terms, etc.). */
    public static final Pattern NUMERIC_OR_LONG_SPANISH_DATE =
            Pattern.compile(
                    "\\b\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}\\b|" + LONG_DATE_REGEX_FRAGMENT, UNICODE);

    /** Document-bound / routing: ISO, numeric DMY, or long Spanish. */
    public static final Pattern QUERY_DATE_SIGNAL =
            Pattern.compile(
                    "(\\b\\d{4}-\\d{1,2}-\\d{1,2}\\b)|(\\b\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}\\b)|"
                            + "(\\b\\d{1,2}\\s+de\\s+\\p{L}+\\s+de[l]?\\s+\\d{4}\\b)",
                    UNICODE);

    private static final Map<String, Integer> SPANISH_MONTHS =
            Map.ofEntries(
                    Map.entry("enero", 1),
                    Map.entry("febrero", 2),
                    Map.entry("marzo", 3),
                    Map.entry("abril", 4),
                    Map.entry("mayo", 5),
                    Map.entry("junio", 6),
                    Map.entry("julio", 7),
                    Map.entry("agosto", 8),
                    Map.entry("septiembre", 9),
                    Map.entry("setiembre", 9),
                    Map.entry("octubre", 10),
                    Map.entry("noviembre", 11),
                    Map.entry("diciembre", 12));

    private static final List<DateTimeFormatter> FLEXIBLE_FORMATTERS =
            List.of(
                    DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es")),
                    DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es")),
                    DateTimeFormatter.ofPattern("d de MMMM de yyyy", Locale.forLanguageTag("es")),
                    DateTimeFormatter.ofPattern("dd de MMMM de yyyy", Locale.forLanguageTag("es")),
                    DateTimeFormatter.ofPattern("d 'de' MMM 'de' yyyy", Locale.forLanguageTag("es")),
                    DateTimeFormatter.ofPattern("dd 'de' MMM 'de' yyyy", Locale.forLanguageTag("es")),
                    DateTimeFormatter.ofPattern("d de MMM de yyyy", Locale.forLanguageTag("es")),
                    DateTimeFormatter.ofPattern("dd de MMM de yyyy", Locale.forLanguageTag("es")),
                    DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("es")),
                    DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.forLanguageTag("es")),
                    DateTimeFormatter.ofPattern("d/M/yyyy"),
                    DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                    DateTimeFormatter.ofPattern("d-M-yyyy"),
                    DateTimeFormatter.ofPattern("dd-MM-yyyy"),
                    DateTimeFormatter.ofPattern("yyyy/MM/dd"),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                    DateTimeFormatter.ofPattern("yyyy.MM.dd"),
                    DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es")),
                    DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH));

    private QueryDateSupport() {}

    public static boolean hasLongSpanishDateInText(String text) {
        return text != null && !text.isBlank() && LONG_DATE_PHRASE.matcher(text).find();
    }

    public static boolean hasCompactSpanishDateInText(String text) {
        return text != null && !text.isBlank() && SPANISH_DAY_MONTH_YEAR_NO_DE.matcher(text).find();
    }

    public static boolean hasIsoDateInText(String text) {
        return text != null && !text.isBlank() && ISO_DATE.matcher(text).find();
    }

    public static boolean hasNumericDmyInText(String text) {
        return ActaSlashDateSupport.hasSlashOrDashDateInText(text);
    }

    public static boolean hasYearTemporalAnchorInText(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return YEAR_ANCHOR.matcher(text).find()
                || DEL_YEAR_ANCHOR.matcher(text).find()
                || IN_YEAR.matcher(text).find();
    }

    /**
     * True when the text contains a date that can be parsed to day precision (ISO, DMY, or long Spanish).
     */
    public static boolean hasParseableDateInText(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return hasIsoDateInText(text)
                || hasNumericDmyInText(text)
                || hasLongSpanishDateInText(text)
                || hasCompactSpanishDateInText(text);
    }

    /**
     * Broad temporal signal for acta anchoring: parseable dates plus year-only anchors ({@code año 2025},
     * {@code del año 2025}, {@code en 2025}).
     */
    public static boolean hasExplicitDateSignalInText(String text) {
        return hasParseableDateInText(text) || hasYearTemporalAnchorInText(text);
    }

    public static List<String> findLongDatePhrases(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Set<String> out = new LinkedHashSet<>();
        Matcher matcher = LONG_DATE_PHRASE.matcher(text);
        while (matcher.find()) {
            out.add(matcher.group(1).trim());
        }
        return List.copyOf(out);
    }

    /**
     * Extracts date-like substrings from query text (ISO, numeric, long Spanish, year anchors).
     * Order is deterministic; duplicates removed.
     */
    public static List<String> extractDateCandidatesFromText(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Set<String> out = new LinkedHashSet<>();
        collectCaptureGroups(ISO_DATE, text, out);
        collectCaptureGroups(DMY_SLASH_OR_DASH_CAPTURE, text, out);
        collectCaptureGroups(DMY_DOT_CAPTURE, text, out);
        out.addAll(findLongDatePhrases(text));
        collectCaptureGroups(SPANISH_DAY_MONTH_YEAR_NO_DE, text, out);
        Matcher yearAnchor = YEAR_ANCHOR.matcher(text);
        if (yearAnchor.find()) {
            out.add(yearAnchor.group(1) + "-01-01");
        }
        return List.copyOf(out);
    }

    public static Optional<LocalDate> parseLongDatePhrase(String phrase) {
        if (phrase == null || phrase.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = LONG_DATE_PARSE.matcher(phrase.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        try {
            int day = Integer.parseInt(matcher.group(1));
            int month = spanishMonthToNumber(matcher.group(2));
            int year = Integer.parseInt(matcher.group(3));
            if (month < 1 || month > 12 || day < 1 || day > 31) {
                return Optional.empty();
            }
            return Optional.of(
                    LocalDate.of(year, month, Math.min(day, LocalDate.of(year, month, 1).lengthOfMonth())));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    /**
     * Flexible parser: ISO, locale formatters, slash/dash via {@link ActaSlashDateSupport}, then long Spanish.
     */
    public static Optional<LocalDate> parseFlexible(String dateStr) {
        return Optional.ofNullable(parseFlexibleOrNull(dateStr));
    }

    public static LocalDate parseFlexibleOrNull(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        String v = dateStr.trim().toLowerCase(Locale.ROOT);
        try {
            return LocalDate.parse(v, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ignored) {
            // Continue with flexible parsers.
        }
        for (DateTimeFormatter formatter : FLEXIBLE_FORMATTERS) {
            try {
                return LocalDate.parse(v, formatter);
            } catch (DateTimeParseException ignored) {
                // Try next formatter.
            }
        }
        Optional<String> slashIso = ActaSlashDateSupport.parseToIso(v);
        if (slashIso.isPresent()) {
            try {
                return LocalDate.parse(slashIso.get(), DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException ignored) {
                // Fall through.
            }
        }
        return parseLongDatePhrase(v).orElseGet(() -> parseCompactSpanishDatePhrase(v).orElse(null));
    }

    public static Optional<LocalDate> parseCompactSpanishDatePhrase(String phrase) {
        if (phrase == null || phrase.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = SPANISH_DAY_MONTH_YEAR_NO_DE.matcher(phrase.trim());
        if (!matcher.find()) {
            return Optional.empty();
        }
        try {
            int day = Integer.parseInt(matcher.group(1));
            int month = spanishMonthToNumber(matcher.group(2));
            int year = Integer.parseInt(matcher.group(3));
            if (month < 1 || month > 12 || day < 1 || day > 31) {
                return Optional.empty();
            }
            return Optional.of(
                    LocalDate.of(year, month, Math.min(day, LocalDate.of(year, month, 1).lengthOfMonth())));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    public static int spanishMonthToNumber(String monthName) {
        if (monthName == null) {
            return -1;
        }
        String normalized =
                monthName.trim().toLowerCase(Locale.ROOT)
                        .replace('á', 'a')
                        .replace('é', 'e')
                        .replace('í', 'i')
                        .replace('ó', 'o')
                        .replace('ú', 'u');
        return SPANISH_MONTHS.getOrDefault(normalized, -1);
    }

    public static Optional<LocalDate> firstParseableDateInText(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        for (String candidate : extractDateCandidatesFromText(text)) {
            Optional<LocalDate> parsed = parseFlexible(candidate);
            if (parsed.isPresent()) {
                return parsed;
            }
        }
        return Optional.empty();
    }

    public static Optional<String> firstNormalizedIsoDateInText(String text) {
        return firstParseableDateInText(text)
                .map(d -> d.format(DateTimeFormatter.ISO_LOCAL_DATE));
    }

    private static void collectCaptureGroups(Pattern pattern, String text, Set<String> out) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            out.add(matcher.group(1).trim());
        }
    }
}

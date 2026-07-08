package com.uniovi.rag.application.service.runtime.query;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses Spanish slash/dash acta dates including two-digit years (25/02/26 → 2026-02-25). */
public final class ActaSlashDateSupport {

    private static final Pattern SLASH_OR_DASH_DATE =
            Pattern.compile("\\b(\\d{1,2})[/-](\\d{1,2})[/-](\\d{2,4})\\b");

    private ActaSlashDateSupport() {}

    public static boolean hasSlashOrDashDateInText(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return SLASH_OR_DASH_DATE.matcher(text).find();
    }

    public static Optional<String> parseToIso(String dateToken) {
        if (dateToken == null || dateToken.isBlank()) {
            return Optional.empty();
        }
        Matcher m = SLASH_OR_DASH_DATE.matcher(dateToken.trim());
        if (!m.find()) {
            return Optional.empty();
        }
        return parsePartsToIso(m.group(1), m.group(2), m.group(3));
    }

    public static Optional<String> firstIsoDateInText(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        Matcher m = SLASH_OR_DASH_DATE.matcher(text);
        if (!m.find()) {
            return Optional.empty();
        }
        return parsePartsToIso(m.group(1), m.group(2), m.group(3));
    }

    public static Optional<String> firstSlashDateTokenInText(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        Matcher m = SLASH_OR_DASH_DATE.matcher(text);
        if (!m.find()) {
            return Optional.empty();
        }
        return Optional.of(m.group());
    }

    private static Optional<String> parsePartsToIso(String dayPart, String monthPart, String yearPart) {
        try {
            int day = Integer.parseInt(dayPart);
            int month = Integer.parseInt(monthPart);
            int year = resolveYear(yearPart);
            LocalDate date = LocalDate.of(year, month, day);
            return Optional.of(date.format(DateTimeFormatter.ISO_LOCAL_DATE));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    static int resolveYear(String yearPart) {
        int y = Integer.parseInt(yearPart);
        if (yearPart.length() == 2) {
            return 2000 + y;
        }
        return y;
    }

    /** Display form dd/MM/yyyy for follow-up expansion when ISO is known. */
    public static String isoToSlashDisplay(String iso) {
        if (iso == null || iso.isBlank()) {
            return "";
        }
        try {
            LocalDate d = LocalDate.parse(iso.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
            return String.format(Locale.ROOT, "%02d/%02d/%04d", d.getDayOfMonth(), d.getMonthValue(), d.getYear());
        } catch (DateTimeParseException e) {
            return iso;
        }
    }
}

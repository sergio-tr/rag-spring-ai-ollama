package com.uniovi.rag.application.service.evaluation.metrics.matching;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative value extraction for scoring-only numeric, date, and entity matching. */
public final class ScoringValueExtractor {

    private static final Pattern INTEGER_PATTERN = Pattern.compile("-?\\d+");
    private static final Pattern ISO_DATE = Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2})\\b");
    private static final Pattern DMY_NUMERIC = Pattern.compile("\\b(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4})\\b");
    private static final Pattern SPANISH_DATE =
            Pattern.compile(
                    "\\b(\\d{1,2})\\s+de\\s+(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|octubre|noviembre|diciembre)(?:\\s+de\\s+(\\d{4}))?",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern DURATION_MINUTES =
            Pattern.compile("(\\d+)\\s*(minutos?|mins?|minutes?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DURATION_HOURS =
            Pattern.compile("(\\d+)\\s*(horas?|hrs?|hours?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIME_RANGE =
            Pattern.compile(
                    "(\\d{1,2})[:.](\\d{2})\\s*(?:a|hasta|-|–)\\s*(\\d{1,2})[:.](\\d{2})",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern START_END_CLOCK_TIMES =
            Pattern.compile(
                    "(?:comenz|inici|empez|start).{0,40}?(\\d{1,2})[:.](\\d{2}).{0,80}?(?:termin|finaliz|acab|end).{0,40}?(\\d{1,2})[:.](\\d{2})",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern SPANISH_HALF_HOUR =
            Pattern.compile(
                    "(?:una?\\s+)?hora\\s+y\\s+media",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern HOURS_AND_MINUTES =
            Pattern.compile(
                    "(\\d+)\\s+horas?\\s+y\\s+(\\d+)\\s+minutos?",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern ACTA_ORDINAL =
            Pattern.compile("\\bacta\\s*#?\\s*\\d+\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern COUNT_CONTEXT =
            Pattern.compile(
                    "\\b(cero|uno|un|una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce|trece|catorce|quince|veinte|\\d+)\\s+(veces|ocasiones|reuniones|actas|documentos|asistentes|personas|miembros|entradas|items|registros)\\b",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern STANDALONE_COUNT_WORD =
            Pattern.compile(
                    "\\b(cero|uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce|trece|catorce|quince|veinte)\\b",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Map<String, Integer> SPANISH_NUMBERS =
            Map.ofEntries(
                    Map.entry("cero", 0),
                    Map.entry("uno", 1),
                    Map.entry("un", 1),
                    Map.entry("una", 1),
                    Map.entry("dos", 2),
                    Map.entry("tres", 3),
                    Map.entry("cuatro", 4),
                    Map.entry("cinco", 5),
                    Map.entry("seis", 6),
                    Map.entry("siete", 7),
                    Map.entry("ocho", 8),
                    Map.entry("nueve", 9),
                    Map.entry("diez", 10),
                    Map.entry("once", 11),
                    Map.entry("doce", 12),
                    Map.entry("trece", 13),
                    Map.entry("catorce", 14),
                    Map.entry("quince", 15),
                    Map.entry("veinte", 20));

    private ScoringValueExtractor() {}

    public static Optional<Integer> extractPrimaryCount(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        Matcher context = COUNT_CONTEXT.matcher(ExpectedAnswerNormalizer.normalizedFold(text));
        if (context.find()) {
            Integer parsed = parseCountToken(context.group(1));
            if (parsed != null) {
                return Optional.of(parsed);
            }
        }
        Integer standalone = firstStandaloneCountWord(text);
        if (standalone != null) {
            return Optional.of(standalone);
        }
        return extractNonDateInteger(text);
    }

    public static Set<LocalDate> extractDates(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        Set<LocalDate> dates = new LinkedHashSet<>();
        Matcher iso = ISO_DATE.matcher(text);
        while (iso.find()) {
            parseIso(iso.group(1)).ifPresent(dates::add);
        }
        Matcher dmy = DMY_NUMERIC.matcher(text);
        while (dmy.find()) {
            parseDmy(dmy.group(1), dmy.group(2), dmy.group(3)).ifPresent(dates::add);
        }
        Matcher spanish = SPANISH_DATE.matcher(text);
        while (spanish.find()) {
            int day = Integer.parseInt(spanish.group(1));
            int month = spanishMonth(spanish.group(2));
            int year =
                    spanish.group(3) != null
                            ? Integer.parseInt(spanish.group(3))
                            : LocalDate.now().getYear();
            if (month > 0) {
                dates.add(LocalDate.of(year, month, day));
            }
        }
        return Set.copyOf(dates);
    }

    public static Optional<Integer> extractDurationMinutes(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        Matcher range = TIME_RANGE.matcher(text);
        if (range.find()) {
            int start = Integer.parseInt(range.group(1)) * 60 + Integer.parseInt(range.group(2));
            int end = Integer.parseInt(range.group(3)) * 60 + Integer.parseInt(range.group(4));
            if (end >= start) {
                return Optional.of(end - start);
            }
        }
        Matcher startEnd = START_END_CLOCK_TIMES.matcher(text);
        if (startEnd.find()) {
            int start = Integer.parseInt(startEnd.group(1)) * 60 + Integer.parseInt(startEnd.group(2));
            int end = Integer.parseInt(startEnd.group(3)) * 60 + Integer.parseInt(startEnd.group(4));
            if (end >= start) {
                return Optional.of(end - start);
            }
        }
        Matcher hoursMinutes = HOURS_AND_MINUTES.matcher(text);
        if (hoursMinutes.find()) {
            return Optional.of(
                    Integer.parseInt(hoursMinutes.group(1)) * 60 + Integer.parseInt(hoursMinutes.group(2)));
        }
        if (SPANISH_HALF_HOUR.matcher(text).find()) {
            return Optional.of(90);
        }
        Matcher hours = DURATION_HOURS.matcher(text);
        if (hours.find()) {
            return Optional.of(Integer.parseInt(hours.group(1)) * 60);
        }
        Matcher minutes = DURATION_MINUTES.matcher(text);
        if (minutes.find()) {
            return Optional.of(Integer.parseInt(minutes.group(1)));
        }
        return Optional.empty();
    }

    public static EntityMatchScore entityRecall(String expected, String actual) {
        Set<String> exp = ExpectedAnswerNormalizer.normalizedTokens(expected, ExpectedAnswerNormalizer.TokenMode.ENTITY);
        Set<String> act = ExpectedAnswerNormalizer.normalizedTokens(actual, ExpectedAnswerNormalizer.TokenMode.ENTITY);
        if (exp.size() < 2) {
            return EntityMatchScore.unsafe("expected_entity_tokens_insufficient");
        }
        if (act.isEmpty()) {
            return EntityMatchScore.noMatch("actual_entity_tokens_empty");
        }
        int hits = 0;
        for (String token : exp) {
            if (act.contains(token)) {
                hits++;
            }
        }
        double recall = (double) hits / exp.size();
        return new EntityMatchScore(recall, recall >= 0.6, false, "entity_recall_" + hits + "_of_" + exp.size());
    }

    private static Optional<Integer> extractNonDateInteger(String text) {
        Set<LocalDate> dates = extractDates(text);
        String withoutDates = text;
        for (LocalDate date : dates) {
            withoutDates = withoutDates.replace(date.toString(), " ");
        }
        Matcher dmy = DMY_NUMERIC.matcher(withoutDates);
        while (dmy.find()) {
            withoutDates = withoutDates.replace(dmy.group(), " ");
        }
        withoutDates = ACTA_ORDINAL.matcher(withoutDates).replaceAll(" ");
        Matcher m = INTEGER_PATTERN.matcher(withoutDates);
        while (m.find()) {
            try {
                return Optional.of(Integer.parseInt(m.group()));
            } catch (NumberFormatException ignored) {
                // continue
            }
        }
        return Optional.empty();
    }

    private static Integer firstStandaloneCountWord(String text) {
        String folded = ExpectedAnswerNormalizer.normalizedFold(text);
        if (extractDates(text).size() == 1 && !COUNT_CONTEXT.matcher(folded).find()) {
            return null;
        }
        Matcher m = STANDALONE_COUNT_WORD.matcher(folded);
        if (m.find()) {
            return parseCountToken(m.group(1));
        }
        return null;
    }

    private static Integer parseCountToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String lower = token.toLowerCase(Locale.ROOT);
        if (SPANISH_NUMBERS.containsKey(lower)) {
            return SPANISH_NUMBERS.get(lower);
        }
        try {
            return Integer.parseInt(lower);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Optional<LocalDate> parseIso(String iso) {
        try {
            return Optional.of(LocalDate.parse(iso));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private static Optional<LocalDate> parseDmy(String day, String month, String year) {
        try {
            return Optional.of(
                    LocalDate.of(
                            Integer.parseInt(year),
                            Integer.parseInt(month),
                            Integer.parseInt(day)));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private static int spanishMonth(String month) {
        if (month == null) {
            return -1;
        }
        return switch (month.toLowerCase(Locale.ROOT)) {
            case "enero" -> 1;
            case "febrero" -> 2;
            case "marzo" -> 3;
            case "abril" -> 4;
            case "mayo" -> 5;
            case "junio" -> 6;
            case "julio" -> 7;
            case "agosto" -> 8;
            case "septiembre" -> 9;
            case "octubre" -> 10;
            case "noviembre" -> 11;
            case "diciembre" -> 12;
            default -> -1;
        };
    }

    public record EntityMatchScore(double recall, boolean matched, boolean unsafe, String reason) {
        static EntityMatchScore unsafe(String reason) {
            return new EntityMatchScore(0.0, false, true, reason);
        }

        static EntityMatchScore noMatch(String reason) {
            return new EntityMatchScore(0.0, false, false, reason);
        }
    }
}

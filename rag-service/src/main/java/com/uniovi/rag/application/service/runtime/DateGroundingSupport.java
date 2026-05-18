package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deterministic date grounding helpers for meeting-minute RAG. */
public final class DateGroundingSupport {

    private static final Pattern ISO_DATE = Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2})\\b");
    private static final Pattern DMY_NUMERIC = Pattern.compile("\\b(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4})\\b");
    private static final Pattern D_DE_M_DE_Y = Pattern.compile(
            "\\b(\\d{1,2})\\s+de\\s+([a-záéíóúñ]+)\\s+de\\s+(\\d{4})\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MONTH_YEAR = Pattern.compile(
            "\\b([a-záéíóúñ]+)\\s+(\\d{4})\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern YEAR = Pattern.compile("\\b(20\\d{2}|19\\d{2})\\b");

    private DateGroundingSupport() {}

    public static Optional<RequestedDate> requestedDate(String rawQuestion) {
        if (rawQuestion == null || rawQuestion.isBlank()) {
            return Optional.empty();
        }
        Optional<LocalDate> exact = firstExactDate(rawQuestion);
        if (exact.isPresent()) {
            return Optional.of(new RequestedDate(exact.get().format(DateTimeFormatter.ISO_LOCAL_DATE), DatePrecision.DAY));
        }
        Matcher monthYear = MONTH_YEAR.matcher(rawQuestion);
        while (monthYear.find()) {
            int month = spanishMonthToInt(monthYear.group(1));
            int year = safeInt(monthYear.group(2));
            if (month >= 1 && year > 0) {
                return Optional.of(new RequestedDate(YearMonth.of(year, month).toString(), DatePrecision.MONTH));
            }
        }
        Matcher year = YEAR.matcher(rawQuestion);
        if (year.find()) {
            return Optional.of(new RequestedDate(year.group(1), DatePrecision.YEAR));
        }
        return Optional.empty();
    }

    public static Optional<RequestedDate> requestedDate(String rawQuestion, List<String> extractedDateHints) {
        Optional<RequestedDate> fromQuestion = requestedDate(rawQuestion);
        if (fromQuestion.isPresent()) {
            return fromQuestion;
        }
        if (extractedDateHints == null || extractedDateHints.isEmpty()) {
            return Optional.empty();
        }
        for (String hint : extractedDateHints) {
            Optional<RequestedDate> parsed = requestedDate(hint);
            if (parsed.isPresent()) {
                return parsed;
            }
        }
        return Optional.empty();
    }

    public static CandidateDateProfile profile(RetrievalCandidate candidate) {
        if (candidate == null) {
            return new CandidateDateProfile("", Optional.empty(), "unknown");
        }
        Map<String, Object> meta = candidate.metadata() != null ? candidate.metadata() : Map.of();
        for (String key : List.of("detectedDate", "documentDate", "date_iso", "date", "meetingDate")) {
            Object raw = meta.get(key);
            Optional<LocalDate> parsed = parseDate(String.valueOf(raw));
            if (parsed.isPresent()) {
                return profile(parsed.get(), "metadata:" + key);
            }
        }
        Object filename = meta.get("filename");
        Optional<LocalDate> fromFilename = firstExactDate(filename != null ? String.valueOf(filename) : "");
        if (fromFilename.isPresent()) {
            return profile(fromFilename.get(), "filename");
        }
        Optional<LocalDate> fromContent = firstExactDate(candidate.content());
        if (fromContent.isPresent()) {
            return profile(fromContent.get(), "content");
        }
        return new CandidateDateProfile("", Optional.empty(), "unknown");
    }

    public static boolean candidateMatchesRequestedDate(RetrievalCandidate candidate, RequestedDate requested) {
        if (requested == null) {
            return false;
        }
        CandidateDateProfile profile = profile(candidate);
        if (profile.date().isEmpty()) {
            return false;
        }
        return switch (requested.precision()) {
            case DAY -> profile.isoDate().equals(requested.value());
            case MONTH -> profile.date().map(d -> YearMonth.from(d).toString().equals(requested.value())).orElse(false);
            case YEAR -> profile.date().map(d -> Integer.toString(d.getYear()).equals(requested.value())).orElse(false);
        };
    }

    public static List<RetrievalCandidate> exactDateCandidates(List<RetrievalCandidate> candidates, RequestedDate requested) {
        if (candidates == null || candidates.isEmpty() || requested == null) {
            return List.of();
        }
        return candidates.stream().filter(c -> candidateMatchesRequestedDate(c, requested)).toList();
    }

    public static List<RetrievalCandidate> preferExactDate(List<RetrievalCandidate> candidates, RequestedDate requested) {
        if (candidates == null || candidates.isEmpty() || requested == null) {
            return candidates == null ? List.of() : candidates;
        }
        List<RetrievalCandidate> exact = exactDateCandidates(candidates, requested);
        if (!exact.isEmpty()) {
            return exact;
        }
        return candidates.stream().sorted(Comparator.comparingInt(c -> mismatchDistance(c, requested))).toList();
    }

    public static DateGroundingDecision decision(String rawQuestion, List<RetrievalCandidate> candidates) {
        Optional<RequestedDate> requested = requestedDate(rawQuestion);
        return decision(requested, candidates);
    }

    public static DateGroundingDecision decision(
            String rawQuestion,
            List<String> extractedDateHints,
            List<RetrievalCandidate> candidates) {
        Optional<RequestedDate> requested = requestedDate(rawQuestion, extractedDateHints);
        return decision(requested, candidates);
    }

    private static DateGroundingDecision decision(
            Optional<RequestedDate> requested,
            List<RetrievalCandidate> candidates) {
        List<RetrievalCandidate> safeCandidates = candidates != null ? candidates : List.of();
        if (requested.isEmpty()) {
            return DateGroundingDecision.notRequested(safeCandidates);
        }
        List<RetrievalCandidate> exact = exactDateCandidates(safeCandidates, requested.get());
        List<String> sourceDates = sourceDates(safeCandidates);
        List<String> matchedDates = sourceDates(exact);
        boolean mismatch = exact.isEmpty() && !safeCandidates.isEmpty();
        String abstentionReason = exact.isEmpty() && safeCandidates.isEmpty()
                ? "no_source_candidates"
                : mismatch ? "no_exact_date_source" : "";
        return new DateGroundingDecision(
                requested.get(),
                sourceDates,
                matchedDates,
                !exact.isEmpty(),
                mismatch,
                abstentionReason,
                nearestCandidates(safeCandidates, requested.get(), 3));
    }

    public static String mismatchMessage(String rawQuestion, DateGroundingDecision decision) {
        if (decision == null || decision.requestedDate() == null) {
            return "";
        }
        boolean es = looksSpanish(rawQuestion);
        String requested = decision.requestedDate().display();
        List<String> alternatives = decision.closestSourceDates().stream()
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .toList();
        String alt = alternatives.isEmpty() ? "" : String.join(", ", alternatives);
        if (es) {
            if (alt.isBlank()) {
                return "No he encontrado un acta con fecha " + requested + " en las fuentes recuperadas.";
            }
            return "No he encontrado un acta con fecha " + requested
                    + ". Las fuentes recuperadas corresponden a " + alt
                    + "; no las uso como si fueran el acta solicitada.";
        }
        if (alt.isBlank()) {
            return "I could not find minutes dated " + requested + " in the retrieved sources.";
        }
        return "I could not find minutes dated " + requested
                + ". The retrieved sources are dated " + alt
                + "; I will not treat them as the requested minutes.";
    }

    public static String traceMessage(DateGroundingDecision decision) {
        if (decision == null || decision.requestedDate() == null) {
            return "requestedDate= requestedDatePrecision= exactDateMatch=false dateMismatchDetected=false sourceDates=[] matchedDocumentDates=[] abstentionReason= groundingPolicyApplied=DATE_GROUNDING_NOT_REQUESTED";
        }
        return "requestedDate=" + decision.requestedDate().value()
                + " requestedDatePrecision=" + decision.requestedDate().precision().name()
                + " exactDateMatch=" + decision.exactDateMatch()
                + " dateMismatchDetected=" + decision.dateMismatchDetected()
                + " sourceDates=" + String.join("|", decision.sourceDates())
                + " matchedDocumentDates=" + String.join("|", decision.matchedDocumentDates())
                + " exactDocumentMatch=" + decision.exactDateMatch()
                + " topSourceDate=" + firstOrBlank(decision.sourceDates())
                + " closestAvailableDate=" + firstOrBlank(decision.closestSourceDates())
                + " abstentionReason=" + decision.abstentionReason()
                + " groundingPolicyApplied=DATE_AWARE_SOURCE_GROUNDING";
    }

    private static String firstOrBlank(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        String first = values.get(0);
        return first != null ? first : "";
    }

    private static CandidateDateProfile profile(LocalDate date, String source) {
        return new CandidateDateProfile(date.format(DateTimeFormatter.ISO_LOCAL_DATE), Optional.of(date), source);
    }

    private static List<String> sourceDates(List<RetrievalCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (RetrievalCandidate c : candidates) {
            CandidateDateProfile p = profile(c);
            if (!p.isoDate().isBlank()) {
                out.add(p.isoDate());
            }
        }
        return List.copyOf(out);
    }

    private static List<String> nearestCandidates(List<RetrievalCandidate> candidates, RequestedDate requested, int max) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        Map<String, String> ordered = new LinkedHashMap<>();
        candidates.stream()
                .sorted(Comparator.comparingInt(c -> mismatchDistance(c, requested)))
                .forEach(c -> {
                    CandidateDateProfile p = profile(c);
                    if (!p.isoDate().isBlank()) {
                        ordered.putIfAbsent(filename(c), p.isoDate());
                    }
                });
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> e : ordered.entrySet()) {
            if (out.size() >= max) {
                break;
            }
            out.add(e.getKey().isBlank() ? e.getValue() : e.getKey() + " (" + e.getValue() + ")");
        }
        return out;
    }

    private static int mismatchDistance(RetrievalCandidate candidate, RequestedDate requested) {
        CandidateDateProfile p = profile(candidate);
        if (p.date().isEmpty() || requested == null) {
            return Integer.MAX_VALUE / 2;
        }
        LocalDate d = p.date().get();
        return switch (requested.precision()) {
            case DAY -> Math.abs((int) (d.toEpochDay() - parseRequestedDay(requested).map(LocalDate::toEpochDay).orElse(0L)));
            case MONTH -> parseRequestedMonth(requested)
                    .map(ym -> Math.abs((d.getYear() - ym.getYear()) * 12 + d.getMonthValue() - ym.getMonthValue()))
                    .orElse(9999);
            case YEAR -> Math.abs(d.getYear() - safeInt(requested.value())) * 366;
        };
    }

    private static Optional<LocalDate> parseRequestedDay(RequestedDate requested) {
        if (requested == null || requested.precision() != DatePrecision.DAY) {
            return Optional.empty();
        }
        return parseDate(requested.value());
    }

    private static Optional<YearMonth> parseRequestedMonth(RequestedDate requested) {
        if (requested == null || requested.precision() != DatePrecision.MONTH) {
            return Optional.empty();
        }
        try {
            return Optional.of(YearMonth.parse(requested.value()));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static Optional<LocalDate> firstExactDate(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        Matcher iso = ISO_DATE.matcher(text);
        if (iso.find()) {
            return parseDate(iso.group(1));
        }
        Matcher dmy = DMY_NUMERIC.matcher(text);
        if (dmy.find()) {
            return localDate(safeInt(dmy.group(3)), safeInt(dmy.group(2)), safeInt(dmy.group(1)));
        }
        Matcher spanish = D_DE_M_DE_Y.matcher(text);
        if (spanish.find()) {
            return localDate(safeInt(spanish.group(3)), spanishMonthToInt(spanish.group(2)), safeInt(spanish.group(1)));
        }
        return Optional.empty();
    }

    private static Optional<LocalDate> parseDate(String raw) {
        if (raw == null || raw.isBlank() || "null".equalsIgnoreCase(raw.trim())) {
            return Optional.empty();
        }
        String s = raw.trim();
        try {
            return Optional.of(LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE));
        } catch (DateTimeParseException ignored) {
            return firstExactDate(s);
        }
    }

    private static Optional<LocalDate> localDate(int year, int month, int day) {
        try {
            return Optional.of(LocalDate.of(year, month, day));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static int safeInt(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (Exception e) {
            return -1;
        }
    }

    private static int spanishMonthToInt(String raw) {
        if (raw == null) {
            return -1;
        }
        String m = raw.trim().toLowerCase(Locale.ROOT)
                .replace('á', 'a')
                .replace('é', 'e')
                .replace('í', 'i')
                .replace('ó', 'o')
                .replace('ú', 'u');
        return switch (m) {
            case "enero" -> 1;
            case "febrero" -> 2;
            case "marzo" -> 3;
            case "abril" -> 4;
            case "mayo" -> 5;
            case "junio" -> 6;
            case "julio" -> 7;
            case "agosto" -> 8;
            case "septiembre", "setiembre" -> 9;
            case "octubre" -> 10;
            case "noviembre" -> 11;
            case "diciembre" -> 12;
            default -> -1;
        };
    }

    private static String filename(RetrievalCandidate c) {
        if (c == null || c.metadata() == null) {
            return "";
        }
        Object filename = c.metadata().get("filename");
        return filename != null ? String.valueOf(filename).trim() : "";
    }

    private static boolean looksSpanish(String rawQuestion) {
        if (rawQuestion == null) {
            return true;
        }
        String q = rawQuestion.toLowerCase(Locale.ROOT);
        return q.contains("¿")
                || q.contains("acta")
                || q.contains("reunión")
                || q.contains("reunion")
                || q.contains("presidente")
                || q.contains("secretaria")
                || q.contains("secretario");
    }

    public enum DatePrecision {
        DAY,
        MONTH,
        YEAR
    }

    public record RequestedDate(String value, DatePrecision precision) {
        public String display() {
            return value;
        }
    }

    public record CandidateDateProfile(String isoDate, Optional<LocalDate> date, String source) {}

    public record DateGroundingDecision(
            RequestedDate requestedDate,
            List<String> sourceDates,
            List<String> matchedDocumentDates,
            boolean exactDateMatch,
            boolean dateMismatchDetected,
            String abstentionReason,
            List<String> closestSourceDates) {

        private static DateGroundingDecision notRequested(List<RetrievalCandidate> candidates) {
            List<String> sourceDates = DateGroundingSupport.sourceDates(candidates);
            return new DateGroundingDecision(null, sourceDates, List.of(), false, false, "", List.of());
        }
    }
}

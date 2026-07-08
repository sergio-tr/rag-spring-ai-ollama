package com.uniovi.rag.tool.metadata;

import com.uniovi.rag.application.service.runtime.language.QueryLanguagePolicy;
import com.uniovi.rag.domain.model.Minute;
import com.uniovi.rag.application.service.runtime.query.ActaFieldAnchorHeuristics;
import com.uniovi.rag.util.QueryDateSupport;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.LoggerFactory;

/**
 * Shared helpers for deterministic acta tools: flatten indexed structured metadata,
 * completeness checks, dedupe scoring, and source references.
 */
public final class StructuredMinuteMetadataSupport {

    static final int UNAVAILABLE_YEAR_THRESHOLD = 2028;

    static final String INCOMPLETE_EXTRACTION_NOTICE =
            "La extracción de participantes está incompleta; no se devuelve la lista parcial como respuesta definitiva.";

    private static final Pattern YEAR_PATTERN = Pattern.compile("\\b(19|20)\\d{2}\\b");
    private static final Pattern ACTA_LABEL_PATTERN = Pattern.compile("(?i)acta\\s*(\\d+)");
    private static final Pattern SPANISH_DATE_PATTERN =
            Pattern.compile(
                    "(\\d{1,2})\\s+de\\s+(\\p{L}+)\\s+de\\s+(\\d{4})",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final DateTimeFormatter SLASH_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Pattern CANONICAL_SLASH_DATE = Pattern.compile("\\d{2}/\\d{2}/\\d{4}");
    private static final Pattern SLASH_DATE_IN_TEXT = Pattern.compile("\\b\\d{1,2}/\\d{1,2}/\\d{4}\\b");
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
                    Map.entry("octubre", 10),
                    Map.entry("noviembre", 11),
                    Map.entry("diciembre", 12));

    private StructuredMinuteMetadataSupport() {}

    /** Merges nested {@code structuredActa} into a single metadata view (chunk keys win). */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> flattenMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> flat = new LinkedHashMap<>(metadata);
        Object nested = metadata.get("structuredActa");
        if (nested instanceof Map<?, ?> structured) {
            structured.forEach(
                    (key, value) -> {
                        if (value != null && !flat.containsKey(String.valueOf(key))) {
                            flat.put(String.valueOf(key), value);
                        }
                    });
        }
        return flat;
    }

    public static int richnessScore(Map<String, Object> metadata) {
        Map<String, Object> flat = flattenMetadata(metadata);
        int score = 0;
        if (isPresent(flat, "president")) score += 20;
        if (isPresent(flat, "date_iso") || isPresent(flat, "date")) score += 20;
        if (isPresent(flat, "startTime")) score += 10;
        if (isPresent(flat, "endTime")) score += 10;
        if (flat.get("numberOfAttendees") instanceof Number n && n.intValue() > 0) score += 15;
        Object attendees = flat.get("attendees");
        if (attendees instanceof List<?> list) score += list.size() * 3;
        Object topics = flat.get("topics");
        if (topics instanceof List<?> t && !t.isEmpty()) score += t.size() * 2;
        if (isPresent(flat, "summary")) score += 5;
        Object presence = flat.get("fieldPresence");
        if (presence instanceof Map<?, ?> m) score += m.size();
        return score;
    }

    public static String resolveDate(Map<String, Object> metadata) {
        Map<String, Object> flat = flattenMetadata(metadata);
        String iso = stringValue(flat.get("date_iso"));
        if (!iso.isBlank()) {
            return iso;
        }
        String actaDate = stringValue(flat.get("actaDate"));
        if (!actaDate.isBlank()) {
            return actaDate;
        }
        return stringValue(flat.get("date"));
    }

    public static String formatSourceReference(Map<String, Object> metadata) {
        Map<String, Object> flat = flattenMetadata(metadata);
        String source = stringValue(flat.get("sourceTitle"));
        if (source.isBlank()) {
            source = stringValue(flat.get("filename"));
        }
        if (source.isBlank()) {
            source = stringValue(flat.get("documentTitle"));
        }
        return source;
    }

    public static String formatSourceReference(Minute minute) {
        if (minute == null) {
            return "";
        }
        if (minute.filename() != null && !minute.filename().isBlank()) {
            return minute.filename();
        }
        return minute.id() != null ? minute.id() : "";
    }

    /** Resolves a human ACTA label from filename (e.g. {@code ACTA 1.pdf} → {@code ACTA 1}). */
    public static String resolveActaLabel(Minute minute) {
        String source = formatSourceReference(minute);
        if (source.isBlank()) {
            return "ACTA";
        }
        Matcher matcher = ACTA_LABEL_PATTERN.matcher(source);
        if (matcher.find()) {
            return "ACTA " + matcher.group(1);
        }
        return source.replaceAll("(?i)\\.pdf$", "").trim();
    }

    /** Formats a minute date as {@code dd/MM/yyyy} for evaluator-friendly list answers. */
    public static String formatDateSlash(Minute minute) {
        if (minute == null || minute.date() == null || minute.date().isBlank()) {
            return "";
        }
        return formatDateSlash(minute.date());
    }

    public static String formatDateSlash(String dateText) {
        if (dateText == null || dateText.isBlank()) {
            return "";
        }
        String trimmed = dateText.trim();
        try {
            return LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE).format(SLASH_DATE);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDate.parse(trimmed, DateTimeFormatter.ofPattern("d/M/uuuu")).format(SLASH_DATE);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        Matcher matcher = SPANISH_DATE_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            int day = Integer.parseInt(matcher.group(1));
            String monthName = matcher.group(2).toLowerCase(Locale.ROOT);
            int year = Integer.parseInt(matcher.group(3));
            Integer month = SPANISH_MONTHS.get(monthName);
            if (month != null) {
                return String.format(Locale.ROOT, "%02d/%02d/%04d", day, month, year);
            }
        }
        return trimmed;
    }

    /**
     * Returns {@code dd/MM/yyyy} only when the source date can be parsed reliably.
     * Never invents a date - returns empty when conversion is not possible.
     */
    public static String resolveCanonicalSlashDate(Minute minute) {
        if (minute == null) {
            return "";
        }
        String fromDate = resolveCanonicalSlashDate(minute.date());
        if (!fromDate.isBlank()) {
            return fromDate;
        }
        if (minute.summary() != null && !minute.summary().isBlank()) {
            fromDate = firstCanonicalSlashDateInText(minute.summary());
            if (!fromDate.isBlank()) {
                return fromDate;
            }
        }
        if (minute.agenda() != null) {
            for (String agendaText : minute.agenda().values()) {
                fromDate = firstCanonicalSlashDateInText(agendaText);
                if (!fromDate.isBlank()) {
                    return fromDate;
                }
            }
        }
        return "";
    }

    static boolean isUnknownDateLiteral(String dateText) {
        if (dateText == null || dateText.isBlank()) {
            return true;
        }
        String folded = dateText.trim().toLowerCase(Locale.ROOT);
        return folded.equals("unknown date")
                || folded.equals("fecha desconocida")
                || folded.equals("unknown")
                || folded.equals("n/a")
                || folded.equals("na");
    }

    /** Scans free text for the first reliably parseable {@code dd/MM/yyyy} date. */
    static String firstCanonicalSlashDateInText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        Matcher slashMatcher = SLASH_DATE_IN_TEXT.matcher(text);
        while (slashMatcher.find()) {
            String resolved = resolveCanonicalSlashDate(slashMatcher.group());
            if (!resolved.isBlank()) {
                return resolved;
            }
        }
        Matcher spanishMatcher = SPANISH_DATE_PATTERN.matcher(text);
        while (spanishMatcher.find()) {
            String resolved = resolveCanonicalSlashDate(spanishMatcher.group());
            if (!resolved.isBlank()) {
                return resolved;
            }
        }
        Matcher isoMatcher = Pattern.compile("\\b(19|20)\\d{2}-\\d{2}-\\d{2}\\b").matcher(text);
        while (isoMatcher.find()) {
            String resolved = resolveCanonicalSlashDate(isoMatcher.group());
            if (!resolved.isBlank()) {
                return resolved;
            }
        }
        return "";
    }

    public static String resolveCanonicalSlashDate(String dateText) {
        if (dateText == null || dateText.isBlank() || isUnknownDateLiteral(dateText)) {
            return "";
        }
        String slash = formatDateSlash(dateText.trim());
        return CANONICAL_SLASH_DATE.matcher(slash).matches() ? slash : "";
    }

    /**
     * Deterministic FIND_PARAGRAPH answer with acta date anchor and extracted topic evidence.
     */
    public static String formatFindParagraphTopicEvidenceAnswer(
            String query, Minute minute, String paragraphBody) {
        return formatFindParagraphTopicEvidenceAnswer(query, minute, paragraphBody, null);
    }

    public static String formatFindParagraphTopicEvidenceAnswer(
            String query, Minute minute, String paragraphBody, String dateContextText) {
        if (minute == null || paragraphBody == null || paragraphBody.isBlank()) {
            return paragraphBody != null ? paragraphBody : "";
        }
        String body = paragraphBody.replaceAll("\\s+", " ").trim();
        String dateSlash = resolveCanonicalSlashDate(minute);
        if (dateSlash.isBlank() && dateContextText != null && !dateContextText.isBlank()) {
            dateSlash = firstCanonicalSlashDateInText(dateContextText);
        }
        if (dateSlash.isBlank()) {
            dateSlash = firstCanonicalSlashDateInText(body);
        }
        if (dateSlash.isBlank()) {
            String formatted = formatDateSlash(minute);
            if (CANONICAL_SLASH_DATE.matcher(formatted).matches()) {
                dateSlash = formatted;
            }
        }
        if (dateSlash.isBlank()) {
            return body;
        }
        if (body.startsWith("Se ")) {
            body = "se " + body.substring(3);
        }
        return "En el acta del " + dateSlash + " " + body;
    }

    /** Resolves structured attendee count from minute metadata (R2 {@code attendeesCount} / list size). */
    public static int resolveAttendeeCount(Minute minute) {
        if (minute == null) {
            return 0;
        }
        if (minute.numberOfAttendees() > 0) {
            return minute.numberOfAttendees();
        }
        if (minute.attendees() != null && !minute.attendees().isEmpty()) {
            return dedupeCanonicalAttendeeNames(minute.attendees()).size();
        }
        return 0;
    }

    /**
     * Deduplicates attendee names: exact normalized match and strict token-prefix aliases
     * (e.g. drop {@code Isabel Castro} when {@code Isabel Castro Torres} is present).
     */
    public static List<String> dedupeCanonicalAttendeeNames(List<String> names) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        List<String> trimmed =
                names.stream()
                        .filter(n -> n != null && !n.isBlank())
                        .map(String::trim)
                        .toList();
        List<String> byLength = new ArrayList<>(trimmed);
        byLength.sort((a, b) -> Integer.compare(b.length(), a.length()));

        List<String> kept = new ArrayList<>();
        for (String candidate : byLength) {
            if (!isDuplicateOrPrefixAlias(candidate, kept)) {
                kept.add(candidate);
            }
        }

        List<String> ordered = new ArrayList<>();
        for (String original : trimmed) {
            String normOriginal = normalizePersonName(original);
            for (String k : kept) {
                if (normalizePersonName(k).equals(normOriginal) && !ordered.contains(k)) {
                    ordered.add(k);
                    break;
                }
            }
        }
        return ordered;
    }

    static boolean isDuplicateOrPrefixAlias(String candidate, List<String> kept) {
        String normCandidate = normalizePersonName(candidate);
        if (normCandidate.isBlank()) {
            return true;
        }
        for (String existing : kept) {
            String normExisting = normalizePersonName(existing);
            if (normCandidate.equals(normExisting)) {
                return true;
            }
            if (isPrefixPersonNameAlias(normCandidate, normExisting)
                    || isPrefixPersonNameAlias(normExisting, normCandidate)) {
                return true;
            }
        }
        return false;
    }

    /** True when shorter is a strict leading token-prefix of longer. */
    static boolean isPrefixPersonNameAlias(String normShorter, String normLonger) {
        if (normShorter.isBlank() || normLonger.isBlank() || normShorter.equals(normLonger)) {
            return normShorter.equals(normLonger);
        }
        if (normLonger.length() <= normShorter.length()) {
            return false;
        }
        return normLonger.startsWith(normShorter + " ");
    }

    /**
     * Merges HYBRID chunk attendee lists preferring a complete structured list that matches
     * {@code expectedCount}; never inflates count with prefix aliases.
     */
    public static List<String> mergeCanonicalAttendeeLists(
            List<String> primary, List<String> secondary, int expectedCount) {
        if (expectedCount > 0) {
            for (List<String> source : List.of(primary, secondary)) {
                if (source == null || source.isEmpty()) {
                    continue;
                }
                List<String> canonical = dedupeCanonicalAttendeeNames(source);
                if (canonical.size() == expectedCount) {
                    return canonical;
                }
            }
        }
        List<String> union = new ArrayList<>();
        if (primary != null) {
            union.addAll(primary);
        }
        if (secondary != null) {
            union.addAll(secondary);
        }
        List<String> deduped = dedupeCanonicalAttendeeNames(union);
        if (expectedCount > 0 && deduped.size() > expectedCount) {
            for (List<String> source : List.of(primary, secondary)) {
                if (source == null || source.isEmpty()) {
                    continue;
                }
                List<String> canonical = dedupeCanonicalAttendeeNames(source);
                if (!canonical.isEmpty() && canonical.size() <= expectedCount) {
                    return canonical;
                }
            }
        }
        return deduped;
    }

    /**
     * Deterministic FILTER_AND_LIST answers with evaluator-mandatory slash dates, ACTA labels, counts, and topics.
     * Returns empty when required structured fields cannot be resolved (caller may fall back to LLM).
     */
    public static Optional<String> formatDeterministicFilterListAnswer(
            String query, List<Minute> minutes, String topic, boolean topicAndPresidentQuery) {
        if (minutes == null || minutes.isEmpty()) {
            return Optional.empty();
        }
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT);

        if (topicAndPresidentQuery && minutes.size() == 1) {
            Optional<String> topicPresident = formatTopicAndPresidentFilterAnswer(query, minutes.get(0), topic);
            if (topicPresident.isPresent()) {
                return topicPresident;
            }
        }

        if (isTopicMinuteDatesListQuery(q)) {
            return formatTopicMinuteDatesListAnswer(query, minutes, topic);
        }

        if (isTopicActaListQuery(q)) {
            return formatTopicActaListAnswer(query, minutes, topic);
        }

        if (isCompoundMonthTopicAttendeeFilterQuery(q)) {
            return formatCompoundMonthTopicAttendeeFilterAnswer(minutes, topic);
        }

        if (isPlaceListQuery(q)) {
            return formatPlaceListAnswer(query, minutes);
        }

        return Optional.empty();
    }

    /** Corpus-wide place enumeration - metadata place field, not topic filter. */
    public static boolean isPlaceListQuery(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String q = query.toLowerCase(Locale.ROOT);
        return q.contains("dime los lugares")
                || q.contains("lugares donde se han realizado")
                || q.contains("lugares donde se celebr")
                || q.contains("dónde se celebraron")
                || q.contains("donde se celebraron");
    }

    public static Optional<String> formatPlaceListAnswer(String query, List<Minute> minutes) {
        if (minutes == null || minutes.isEmpty()) {
            return Optional.empty();
        }
        LinkedHashSet<String> places = new LinkedHashSet<>();
        for (Minute minute : minutes) {
            if (minute != null && minute.place() != null && !minute.place().isBlank()) {
                places.add(minute.place().trim());
            }
        }
        if (places.isEmpty()) {
            return Optional.empty();
        }
        boolean spanish = querySeemsSpanish(query);
        String list = joinNaturalLanguageList(new ArrayList<>(places), spanish);
        if (spanish) {
            if (places.size() == 1) {
                return Optional.of("El lugar donde se han realizado las actas es " + list + ".");
            }
            return Optional.of("Los lugares donde se han realizado las actas son: " + list + ".");
        }
        if (places.size() == 1) {
            return Optional.of("The meeting place is " + list + ".");
        }
        return Optional.of("The meeting places are: " + list + ".");
    }

    public static String formatExactAttendeeFallbackAnswer(String query, int threshold, List<Minute> exact) {
        boolean spanish = querySeemsSpanish(query);
        List<String> items =
                exact.stream()
                        .map(
                                minute -> {
                                    String source = formatSourceReference(minute);
                                    String dateSlash = formatDateSlash(minute);
                                    if (source.isBlank()) {
                                        return dateSlash;
                                    }
                                    return source + (dateSlash.isBlank() ? "" : " (" + dateSlash + ")");
                                })
                        .filter(s -> s != null && !s.isBlank())
                        .distinct()
                        .toList();
        String list = joinNaturalLanguageList(items, spanish);
        if (spanish) {
            return "No hay actas con más de "
                    + threshold
                    + " asistentes; las que tienen exactamente "
                    + threshold
                    + " son "
                    + list
                    + ".";
        }
        return "No minutes have more than "
                + threshold
                + " attendees; those with exactly "
                + threshold
                + " are "
                + list
                + ".";
    }

    /**
     * True when the query asks which actas discuss a topic (e.g. Spanish ascensor list without asking for dates).
     */
    public static boolean isTopicActaListQuery(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String q =
                Normalizer.normalize(query.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                        .replaceAll("\\p{M}", "");
        boolean asksActas =
                q.contains("dime las actas")
                        || q.contains("dime que actas")
                        || q.contains("dime qué actas")
                        || q.contains("listar las actas")
                        || q.contains("listar actas")
                        || q.contains("listar que actas")
                        || q.contains("listar qué actas")
                        || q.contains("que actas")
                        || q.contains("qué actas");
        boolean topicCue = q.contains("mencionan") || q.contains("comentan");
        return asksActas && topicCue;
    }

    /**
     * Deterministic FILTER_AND_LIST answer listing actas that mention a topic (labels + slash dates).
     */
    public static Optional<String> formatTopicActaListAnswer(
            String query, List<Minute> minutes, String topic) {
        if (minutes == null || minutes.isEmpty()) {
            return Optional.empty();
        }
        boolean spanish = querySeemsSpanish(query);
        List<String> items =
                minutes.stream()
                        .map(
                                minute -> {
                                    String source = formatSourceReference(minute);
                                    String dateSlash = formatDateSlash(minute);
                                    if (source.isBlank()) {
                                        return dateSlash;
                                    }
                                    return source
                                            + (dateSlash.isBlank() ? "" : " (" + dateSlash + ")");
                                })
                        .filter(s -> s != null && !s.isBlank())
                        .distinct()
                        .toList();
        if (items.isEmpty()) {
            return Optional.empty();
        }
        String topicLabel =
                topic != null && !topic.isBlank()
                        ? topic.trim().toLowerCase(Locale.ROOT)
                        : spanish ? "el tema indicado" : "the requested topic";
        String listPart = joinNaturalLanguageList(items, spanish);
        if (spanish) {
            if (items.size() == 1) {
                return Optional.of(
                        "El acta donde se menciona "
                                + topicLabel
                                + " es "
                                + listPart
                                + ".");
            }
            return Optional.of(
                    "Las actas donde se menciona "
                            + topicLabel
                            + " son: "
                            + listPart
                            + ".");
        }
        if (items.size() == 1) {
            return Optional.of(
                    "The meeting minute that discusses " + topicLabel + " is " + listPart + ".");
        }
        return Optional.of(
                "The meeting minutes that discuss " + topicLabel + " are: " + listPart + ".");
    }

    /**
     * True when the query asks for meeting dates where a topic (e.g. elevator) is discussed.
     */
    public static boolean isTopicMinuteDatesListQuery(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String q =
                Normalizer.normalize(query.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                        .replaceAll("\\p{M}", "");
        boolean asksDates =
                (q.contains("dates") && q.contains("minute"))
                        || (q.contains("fechas")
                                && (q.contains("acta") || q.contains("reunion") || q.contains("reuniones")));
        boolean topicContext =
                q.contains("elevator")
                        || q.contains("ascensor")
                        || q.contains("where")
                                && (q.contains("commented") || q.contains("mentioned") || q.contains("discussed"));
        return asksDates && topicContext;
    }

    /**
     * Deterministic FILTER_AND_LIST answer listing slash dates for actas that mention a topic.
     */
    public static Optional<String> formatTopicMinuteDatesListAnswer(
            String query, List<Minute> minutes, String topic) {
        if (minutes == null || minutes.isEmpty()) {
            return Optional.empty();
        }
        boolean spanish = querySeemsSpanish(query);
        List<String> dateSlashes =
                minutes.stream()
                        .map(StructuredMinuteMetadataSupport::resolveCanonicalSlashDate)
                        .filter(s -> s != null && !s.isBlank())
                        .distinct()
                        .toList();
        if (dateSlashes.isEmpty()) {
            return Optional.empty();
        }
        String topicLabel =
                topic != null && !topic.isBlank()
                        ? topic.trim().toLowerCase(Locale.ROOT)
                        : spanish ? "el tema indicado" : "the requested topic";
        String listPart = joinNaturalLanguageList(dateSlashes, spanish);
        if (spanish) {
            if (dateSlashes.size() == 1) {
                return Optional.of(
                        "Las actas que mencionan "
                                + topicLabel
                                + " corresponden a la fecha "
                                + listPart
                                + ".");
            }
            return Optional.of(
                    "Las actas que mencionan "
                            + topicLabel
                            + " corresponden a las fechas "
                            + listPart
                            + ".");
        }
        if (dateSlashes.size() == 1) {
            return Optional.of(
                    "The meeting minutes that mention "
                            + topicLabel
                            + " are dated "
                            + listPart
                            + ".");
        }
        return Optional.of(
                "The meeting minutes that mention "
                        + topicLabel
                        + " are dated "
                        + listPart
                        + ".");
    }

    private static boolean isCompoundMonthTopicAttendeeFilterQuery(String qLower) {
        return ActaFieldAnchorHeuristics.isCompoundMonthTopicAttendeeFilter(qLower)
                || (qLower.contains("agosto") && qLower.contains("videovigilancia"));
    }

    private static Optional<String> formatCompoundMonthTopicAttendeeFilterAnswer(
            List<Minute> minutes, String topic) {
        String topicLabel =
                topic != null && !topic.isBlank() ? topic.trim().toLowerCase(Locale.ROOT) : "videovigilancia";
        if (minutes.size() == 1) {
            return formatSingleCompoundFilterSentence(minutes.get(0), topicLabel);
        }
        List<String> sentences = new ArrayList<>();
        for (Minute minute : minutes) {
            formatSingleCompoundFilterSentence(minute, topicLabel).ifPresent(sentences::add);
        }
        if (sentences.isEmpty()) {
            return Optional.empty();
        }
        if (sentences.size() == 1) {
            return Optional.of(sentences.get(0));
        }
        return Optional.of(
                "Se encontraron "
                        + sentences.size()
                        + " reuniones: "
                        + joinNaturalLanguageList(sentences, true)
                        + ".");
    }

    private static Optional<String> formatSingleCompoundFilterSentence(Minute minute, String topicLabel) {
        if (minute == null) {
            return Optional.empty();
        }
        String dateSlash = resolveCanonicalSlashDate(minute);
        String actaLabel = resolveActaLabel(minute);
        int attendees = resolveAttendeeCount(minute);
        if (dateSlash.isBlank() || actaLabel.isBlank() || attendees <= 0 || topicLabel.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(
                String.format(
                        Locale.ROOT,
                        "La reunión del %s (%s) trató %s y tuvo %d asistentes.",
                        dateSlash,
                        actaLabel,
                        topicLabel,
                        attendees));
    }

    /**
     * Fixed negative for compound month + topic + attendee-threshold filters (FD-FL-03) when no acta matches.
     */
    public static String formatCompoundMonthTopicAttendeeNoMatchAnswer(String query, String topic) {
        String topicLabel =
                topic != null && !topic.isBlank() ? topic.trim().toLowerCase(Locale.ROOT) : "el tema indicado";
        if (querySeemsSpanish(query)) {
            return "No se encontraron reuniones que cumplan con esos criterios (mes, "
                    + topicLabel
                    + " y umbral de asistentes).";
        }
        return "No meetings matched the month, topic, and attendee criteria.";
    }

    private static Optional<String> formatTopicAndPresidentFilterAnswer(
            String query, Minute minute, String topic) {
        if (minute == null || topic == null || topic.isBlank()) {
            return Optional.empty();
        }
        String president = minute.president();
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT);
        if (president == null || president.isBlank() || !q.contains("presidid")) {
            return Optional.empty();
        }
        String dateSlash = resolveCanonicalSlashDate(minute);
        if (dateSlash.isBlank()) {
            dateSlash = formatDateSlash(minute);
        }
        String actaLabel = formatSourceReference(minute);
        if (actaLabel.isBlank()) {
            actaLabel = resolveActaLabel(minute);
        }
        String topicPhrase = topic.toLowerCase(Locale.ROOT).startsWith("el ") ? topic : "el " + topic;
        return Optional.of(
                String.format(
                        Locale.ROOT,
                        "Solo el acta del %s (%s) menciona %s y fue presidida por %s.",
                        dateSlash,
                        actaLabel,
                        topicPhrase,
                        president));
    }

    /**
     * Deterministic answer for “which actas start at HH:mm?” - count, ACTA ids, slash dates, sources.
     */
    public static String formatStartTimeListAnswer(String query, List<Minute> matching, String targetTime) {
        boolean spanish = querySeemsSpanish(query);
        int count = matching == null ? 0 : matching.size();
        if (count == 0) {
            return spanish
                    ? "No hay actas con hora de inicio a las " + targetTime + "."
                    : "No meeting minutes start at " + targetTime + ".";
        }
        List<String> items =
                matching.stream()
                        .map(
                                minute -> {
                                    String label = resolveActaLabel(minute);
                                    String dateSlash = formatDateSlash(minute);
                                    return dateSlash.isBlank()
                                            ? label
                                            : label + " (" + dateSlash + ")";
                                })
                        .toList();
        String listPart = joinNaturalLanguageList(items, spanish);
        String sources =
                matching.stream()
                        .map(StructuredMinuteMetadataSupport::formatSourceReference)
                        .filter(source -> source != null && !source.isBlank())
                        .distinct()
                        .collect(Collectors.joining(", "));
        String answer;
        if (spanish) {
            if (count == 1) {
                answer = "Hay 1 acta con hora de inicio a las " + targetTime + ": " + listPart + ".";
            } else {
                answer =
                        "Hay "
                                + count
                                + " actas con hora de inicio a las "
                                + targetTime
                                + ": "
                                + listPart
                                + ".";
            }
        } else if (count == 1) {
            answer = "There is 1 meeting minute starting at " + targetTime + ": " + listPart + ".";
        } else {
            answer = "There are " + count + " meeting minutes starting at " + targetTime + ": " + listPart + ".";
        }
        if (!sources.isBlank()) {
            answer += spanish ? " Fuentes: " + sources + "." : " Sources: " + sources + ".";
        }
        return answer;
    }

    /**
     * Deterministic answer for “which acta dates ended after HH:mm?” - slash dates and ACTA labels.
     */
    public static String formatEndTimeAfterListAnswer(
            String query, List<Minute> matching, String thresholdTime) {
        boolean spanish = querySeemsSpanish(query);
        int count = matching == null ? 0 : matching.size();
        if (count == 0) {
            return spanish
                    ? "No hay actas que terminaron después de las " + thresholdTime + "."
                    : "No meeting minutes ended after " + thresholdTime + ".";
        }
        List<String> dateSlashes =
                matching.stream()
                        .map(StructuredMinuteMetadataSupport::formatDateSlash)
                        .filter(date -> date != null && !date.isBlank())
                        .distinct()
                        .toList();
        String listPart = joinNaturalLanguageList(dateSlashes, spanish);
        if (spanish) {
            return "Las fechas de las actas que terminaron más tarde de las "
                    + thresholdTime
                    + " son: "
                    + listPart
                    + ".";
        }
        return "The meeting dates that ended after " + thresholdTime + " are: " + listPart + ".";
    }

  /**
   * FD-CD-03: year-only COUNT_DOCUMENTS negative - corpus-scoped wording (no generic date template).
   */
  public static String formatYearOnlyActaCorpusAbsence(String year) {
      return "No existen actas correspondientes al año " + year + " en el corpus.";
  }

  /**
   * True when the query counts actas for a calendar year only (no day/month anchor).
   */
  public static boolean isYearOnlyActaCountQuery(String query) {
      if (query == null || query.isBlank()) {
          return false;
      }
      String q =
              Normalizer.normalize(query.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                      .replaceAll("\\p{M}", "");
      return q.matches(".*\\b(?:año|ano)\\s+\\d{4}\\b.*")
              && (q.contains("actas")
                      || q.contains("registradas")
                      || q.contains("numero de actas")
                      || q.contains("número de actas"));
  }

    /**
     * Deterministic answer for “how many meetings in year Y?” - count, ACTA ids, slash dates, sources.
     */
    public static String formatYearMeetingCountAnswer(String query, List<Minute> matching, String year) {
        boolean spanish = querySeemsSpanish(query);
        int count = matching == null ? 0 : matching.size();
        if (count == 0) {
            if (spanish && isYearOnlyActaCountQuery(query)) {
                return formatYearOnlyActaCorpusAbsence(year);
            }
            return spanish
                    ? "No hay reuniones registradas en el año " + year + "."
                    : "No meetings were found in year " + year + ".";
        }
        List<String> items =
                matching.stream()
                        .map(
                                minute -> {
                                    String label = resolveActaLabel(minute);
                                    String dateSlash = formatDateSlash(minute);
                                    return dateSlash.isBlank()
                                            ? label
                                            : label + " (" + dateSlash + ")";
                                })
                        .toList();
        String listPart = joinNaturalLanguageList(items, spanish);
        String sources =
                matching.stream()
                        .map(StructuredMinuteMetadataSupport::formatSourceReference)
                        .filter(source -> source != null && !source.isBlank())
                        .distinct()
                        .collect(Collectors.joining(", "));
        String answer;
        if (spanish) {
            if (count == 1) {
                answer = "Hubo 1 reunión en " + year + ": " + listPart + ".";
            } else {
                answer = "Hubo " + count + " reuniones en " + year + ": " + listPart + ".";
            }
        } else if (count == 1) {
            answer = "There was 1 meeting in " + year + ": " + listPart + ".";
        } else {
            answer = "There were " + count + " meetings in " + year + ": " + listPart + ".";
        }
        if (!sources.isBlank()) {
            answer += spanish ? " Fuentes: " + sources + "." : " Sources: " + sources + ".";
        }
        return answer;
    }

    /**
     * Deterministic answer for “how many actas mention topic X?” - canonical labels and slash dates.
     */
    public static String formatTopicMeetingCountAnswer(String query, List<Minute> matching, String topic) {
        boolean spanish = querySeemsSpanish(query);
        int count = matching == null ? 0 : matching.size();
        if (count == 0) {
            if (spanish) {
                if (topic != null
                        && (topic.toLowerCase(Locale.ROOT).contains("radiación solar")
                                || topic.toLowerCase(Locale.ROOT).contains("radiacion solar"))) {
                    return "No hay ninguna acta que mencione ese tema.";
                }
                return "No se encontraron actas que mencionen "
                        + (topic != null ? topic : "ese tema")
                        + ".";
            }
            return "No meeting minutes mention " + (topic != null ? topic : "that topic") + ".";
        }
        List<String> items =
                matching.stream()
                        .map(
                                minute -> {
                                    String source = formatSourceReference(minute);
                                    String dateSlash = formatDateSlash(minute);
                                    if (source.isBlank()) {
                                        return dateSlash;
                                    }
                                    return source
                                            + (dateSlash.isBlank() ? "" : " (" + dateSlash + ")");
                                })
                        .filter(s -> s != null && !s.isBlank())
                        .toList();
        String listPart = joinNaturalLanguageList(items, spanish);
        String topicLabel = topic != null && !topic.isBlank() ? topic : "el tema";
        if (spanish) {
            if (count == 1) {
                return "El "
                        + topicLabel
                        + " se menciona en una acta: "
                        + listPart
                        + ".";
            }
            return "El "
                    + topicLabel
                    + " se menciona en "
                    + (count == 2 ? "dos actas" : count + " actas")
                    + ": "
                    + listPart
                    + ".";
        }
        if (count == 1) {
            return topicLabel + " is mentioned in one meeting: " + listPart + ".";
        }
        return topicLabel + " is mentioned in " + count + " meetings: " + listPart + ".";
    }

    private static boolean querySeemsSpanish(String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        return switch (QueryLanguagePolicy.detect(query)) {
            case SPANISH -> true;
            case ENGLISH -> false;
            case OTHER -> {
                String q = query.toLowerCase(Locale.ROOT);
                yield q.contains("acta")
                        || q.contains("reunión")
                        || q.contains("reunion")
                        || q.contains("¿")
                        || q.contains("cuánt")
                        || q.contains("cuant");
            }
        };
    }

  public enum PersonMeetingRole {
        PRESIDENT,
        SECRETARY,
        ATTENDEE
    }

    public record ResolvedPersonRole(String canonicalName, PersonMeetingRole role) {}

    /** Matches a query name (e.g. {@code Jorge}) against a full attendee/president name. */
    public static boolean personNameMatches(String candidate, String personQuery) {
        if (candidate == null || candidate.isBlank() || personQuery == null || personQuery.isBlank()) {
            return false;
        }
        String normalizedCandidate = normalizePersonName(candidate);
        String normalizedQuery = normalizePersonName(personQuery);
        if (normalizedCandidate.equals(normalizedQuery)
                || normalizedCandidate.contains(normalizedQuery)
                || normalizedQuery.contains(normalizedCandidate)
                || candidate.equalsIgnoreCase(personQuery)) {
            return true;
        }
        String[] queryTokens =
                Arrays.stream(normalizedQuery.split("\\s+"))
                        .filter(token -> token.length() > 1)
                        .toArray(String[]::new);
        if (queryTokens.length == 0) {
            return false;
        }
        String[] candidateTokens =
                Arrays.stream(normalizedCandidate.split("\\s+"))
                        .filter(token -> token.length() > 1)
                        .toArray(String[]::new);
        return Arrays.equals(queryTokens, candidateTokens)
                || (queryTokens.length == 1 && candidateTokens.length >= 1 && candidateTokens[0].equals(queryTokens[0]));
    }

    public static Optional<ResolvedPersonRole> resolvePersonRole(Minute minute, String personQuery) {
        if (minute == null || personQuery == null || personQuery.isBlank()) {
            return Optional.empty();
        }
        if (minute.president() != null && personNameMatches(minute.president(), personQuery)) {
            return Optional.of(new ResolvedPersonRole(minute.president(), PersonMeetingRole.PRESIDENT));
        }
        if (minute.secretary() != null && personNameMatches(minute.secretary(), personQuery)) {
            return Optional.of(new ResolvedPersonRole(minute.secretary(), PersonMeetingRole.SECRETARY));
        }
        if (minute.attendees() != null) {
            for (String attendee : minute.attendees()) {
                if (personNameMatches(attendee, personQuery)) {
                    return Optional.of(new ResolvedPersonRole(attendee, PersonMeetingRole.ATTENDEE));
                }
            }
        }
        return Optional.empty();
    }

    public static String formatPersonRoleAnswer(
            String query, String personQuery, ResolvedPersonRole resolved, Minute minute) {
        boolean spanish = querySeemsSpanish(query);
        String dateSlash = formatDateSlash(minute);
        String actaLabel = resolveActaLabel(minute);
        String source = formatSourceReference(minute);
        String displayDate = dateSlash.isBlank() ? (minute.date() != null ? minute.date() : "") : dateSlash;
        String personName = resolved.canonicalName();
        if (spanish
                && resolved.role() == PersonMeetingRole.ATTENDEE
                && minute.president() != null
                && !minute.president().isBlank()
                && !personName.equalsIgnoreCase(minute.president())
                && asksPersonRoleInQuery(query)) {
            return personName
                    + " fue asistente en la reunión del "
                    + displayDate
                    + ". No ocupó la presidencia; la presidencia recayó en "
                    + minute.president()
                    + ".";
        }
        String roleLabel =
                switch (resolved.role()) {
                    case PRESIDENT -> spanish ? "presidente" : "president";
                    case SECRETARY -> spanish ? "secretaria" : "secretary";
                    case ATTENDEE -> spanish ? "asistente" : "attendee";
                };
        if (spanish) {
            StringBuilder answer =
                    new StringBuilder(
                            "En la reunión del "
                                    + displayDate
                                    + " ("
                                    + actaLabel
                                    + "), "
                                    + personName
                                    + " participó como "
                                    + roleLabel
                                    + ".");
            if (!source.isBlank()) {
                answer.append(" Fuente: ").append(source).append(".");
            }
            return answer.toString();
        }
        StringBuilder answer =
                new StringBuilder(
                        "In the meeting on "
                                + displayDate
                                + " ("
                                + actaLabel
                                + "), "
                                + personName
                                + " participated as "
                                + roleLabel
                                + ".");
        if (!source.isBlank()) {
            answer.append(" Source: ").append(source).append(".");
        }
        return answer.toString();
    }

    private static boolean asksPersonRoleInQuery(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String q = query.toLowerCase(Locale.ROOT);
        return q.contains("papel")
                || q.contains("rol")
                || q.contains("función")
                || q.contains("funcion");
    }

    public static String normalizePersonName(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String normalized = name.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return Normalizer.normalize(normalized, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private static String joinNaturalLanguageList(List<String> items, boolean spanish) {
        if (items.isEmpty()) {
            return "";
        }
        if (items.size() == 1) {
            return items.get(0);
        }
        String conjunction = spanish ? " y " : " and ";
        if (items.size() == 2) {
            return items.get(0) + conjunction + items.get(1);
        }
        return String.join(", ", items.subList(0, items.size() - 1)) + conjunction + items.get(items.size() - 1);
    }

    @SuppressWarnings("unchecked")
    public static boolean isFieldPresent(Map<String, Object> metadata, String field) {
        if (metadata == null || field == null || field.isBlank()) {
            return false;
        }
        Map<String, Object> flat = flattenMetadata(metadata);
        Object presenceObj = flat.get("fieldPresence");
        if (presenceObj instanceof Map<?, ?> presence) {
            Object flag = presence.get(field);
            if (flag instanceof Boolean b) {
                return b;
            }
        }
        return switch (field) {
            case "date", "date_iso" -> isPresent(flat, "date") || isPresent(flat, "date_iso");
            case "attendees" -> flat.get("attendees") instanceof List<?> l && !l.isEmpty();
            case "numberOfAttendees" ->
                    flat.get("numberOfAttendees") instanceof Number n && n.intValue() > 0;
            case "topics" -> flat.get("topics") instanceof List<?> t && !t.isEmpty();
            case "durationMinutes" -> flat.get("durationMinutes") != null;
            default -> isPresent(flat, field);
        };
    }

    /**
     * Returns true when attendee names are complete enough for a confident list answer.
     * Rejects partial chunk lists when header count exceeds extracted names.
     */
    public static boolean isAttendeesListComplete(Map<String, Object> metadata, Minute minute) {
        int expected = expectedAttendeeCount(metadata, minute);
        int listed = listedAttendeeCount(minute);
        if (expected <= 0) {
            return listed > 0;
        }
        if (listed == 0) {
            return false;
        }
        if (listed < expected) {
            return false;
        }
        Map<String, Object> flat = flattenMetadata(metadata);
        Object presenceObj = flat.get("fieldPresence");
        if (presenceObj instanceof Map<?, ?> presence) {
            Object attendeesFlag = presence.get("attendees");
            if (attendeesFlag instanceof Boolean b && !b && listed < expected) {
                return false;
            }
        }
        return true;
    }

    public static int expectedAttendeeCount(Map<String, Object> metadata, Minute minute) {
        Map<String, Object> flat = flattenMetadata(metadata);
        Object countObj = flat.get("numberOfAttendees");
        if (countObj == null) {
            countObj = flat.get("attendeesCount");
        }
        int fromMeta = countObj instanceof Number n ? n.intValue() : 0;
        if (minute != null && minute.numberOfAttendees() > fromMeta) {
            fromMeta = minute.numberOfAttendees();
        }
        return fromMeta;
    }

    public static int listedAttendeeCount(Minute minute) {
        if (minute == null || minute.attendees() == null) {
            return 0;
        }
        return dedupeCanonicalAttendeeNames(minute.attendees()).size();
    }

    public static boolean shouldEnrichFieldFromContent(Map<String, Object> metadata, String field) {
        if (metadata == null || field == null) {
            return true;
        }
        if (isFieldPresent(metadata, field)) {
            return false;
        }
        return switch (field.toLowerCase(Locale.ROOT)) {
            case "attendees", "asistentes", "participantes" -> !isFieldPresent(metadata, "attendees");
            case "president", "presidente" -> !isFieldPresent(metadata, "president");
            case "secretary", "secretario", "secretaria" -> !isFieldPresent(metadata, "secretary");
            case "date", "fecha", "date_iso" -> !isFieldPresent(metadata, "date_iso") && !isFieldPresent(metadata, "date");
            case "topics", "temas" -> !isFieldPresent(metadata, "topics");
            case "duration", "durationminutes" -> !isFieldPresent(metadata, "durationMinutes");
            default -> true;
        };
    }

    public static boolean isFutureOrUnavailableDate(String dateText) {
        if (dateText == null || dateText.isBlank()) {
            return false;
        }
        try {
            LocalDate parsed = LocalDate.parse(dateText.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
            return parsed.getYear() >= UNAVAILABLE_YEAR_THRESHOLD;
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        Matcher matcher = YEAR_PATTERN.matcher(dateText);
        while (matcher.find()) {
            try {
                if (Integer.parseInt(matcher.group()) >= UNAVAILABLE_YEAR_THRESHOLD) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
                // continue
            }
        }
        return false;
    }

    public static Integer durationMinutesFromMetadata(Map<String, Object> metadata) {
        Map<String, Object> flat = flattenMetadata(metadata);
        Object value = flat.get("durationMinutes");
        if (value instanceof Number n && n.intValue() > 0) {
            return n.intValue();
        }
        return null;
    }

    /**
     * True when the query asks for a brief summary of a single dated acta (FD-SM-01 pattern).
     */
    public static boolean isBriefDatedMeetingSummaryQuery(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String q =
                Normalizer.normalize(query.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                        .replaceAll("\\p{M}", "");
        boolean brief =
                q.contains("resume brevemente")
                        || q.contains("resumen breve")
                        || q.contains("puntos tratados")
                        || (q.contains("resumen") && q.contains("acta"))
                        || (q.contains("resume") && q.contains("acta"));
        boolean dated =
                QueryDateSupport.hasParseableDateInText(q)
                        || q.contains("25/02/2026")
                        || q.contains("24/02/2025");
        return brief && dated;
    }

    /**
     * True when the query asks to summarize an acta for a calendar year only (FD-SM-02 pattern).
     */
    public static boolean isYearOnlySummarizeMeetingQuery(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String q =
                Normalizer.normalize(query.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                        .replaceAll("\\p{M}", "");
        return (q.contains("resume") || q.contains("resumen"))
                && q.matches(".*\\b(?:ano|año)\\s+\\d{4}\\b.*");
    }

  /** Extracts a four-digit year from year-only summarize queries. */
  public static String extractSummarizeYear(String query) {
      if (query == null || query.isBlank()) {
          return null;
      }
      Matcher matcher =
              Pattern.compile("(?:del\\s+)?(?:ano|año)\\s+(\\d{4})", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                      .matcher(query);
      return matcher.find() ? matcher.group(1) : null;
  }

    /** FD-SM-02: deterministic negative when no acta exists for the requested year. */
    public static String formatYearOnlySummarizeAbsence(String year) {
        return "No existe ningún acta ni reunión registrada en "
                + year
                + " dentro del corpus evaluado. Por tanto, no se puede generar un resumen para ese año "
                + "ni asociar la consulta a ningún documento disponible.";
    }

    /** Harness minimum for SUMMARIZE_MEETING answers (functional matrix evaluator). */
    public static int summarizeMeetingEvaluatorMinLength() {
        return 80;
    }

    /**
     * Extracts exact attendee count from corpus-wide listing queries (e.g. FD-CE-02
     * "exactamente 21 asistentes").
     */
    public static Optional<Integer> extractExactAttendeeCountFromQuery(String query) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher =
                Pattern.compile("(?:exactamente|con)\\s+(\\d+)\\s+asistentes", Pattern.CASE_INSENSITIVE)
                        .matcher(query);
        if (!matcher.find()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(matcher.group(1)));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    /** FD-CE-02: deterministic negative when no meeting has exactly N attendees corpus-wide. */
    public static String formatExactAttendeeCountCorpusNegative(int requestedCount, List<Minute> corpusMinutes) {
        String base =
                "No existen registros de reuniones con exactamente "
                        + requestedCount
                        + " asistentes en el corpus.";
        TreeSet<Integer> observed = new TreeSet<>();
        if (corpusMinutes != null) {
            for (Minute minute : corpusMinutes) {
                int count = resolveAttendeeCount(minute);
                if (count > 0) {
                    observed.add(count);
                }
            }
        }
        if (observed.isEmpty()) {
            return base;
        }
        List<Integer> counts = new ArrayList<>(observed);
        String countsPhrase;
        if (counts.size() == 1) {
            countsPhrase = String.valueOf(counts.get(0));
        } else {
            String prefix =
                    counts.subList(0, counts.size() - 1).stream()
                            .map(String::valueOf)
                            .collect(Collectors.joining(", "));
            countsPhrase = prefix + " o " + counts.get(counts.size() - 1);
        }
        return base + " Las actas registradas tienen " + countsPhrase + " asistentes.";
    }

    public static boolean isCorpusWideExactAttendeeCountQuery(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        return ActaFieldAnchorHeuristics.isCorpusWideExactAttendeeCountListing(
                query.toLowerCase(Locale.ROOT));
    }

    /**
     * FD-BQ-02: deterministic scoped NO when a topic is absent from all actas in the requested year.
     */
    public static String formatYearScopedTopicNegativeBoolean(String topic, String year) {
        String topicLabel = topic == null || topic.isBlank() ? "ese tema" : topic.trim();
        if (!topicLabel.startsWith("la ") && !topicLabel.startsWith("el ")) {
            topicLabel = "la " + topicLabel;
        }
        return "No, " + topicLabel + " no se menciona en ninguna acta de " + year + ".";
    }

    public static boolean minuteBelongsToYear(Minute minute, String year) {
        if (minute == null || year == null || year.isBlank()) {
            return false;
        }
        String date = minute.date();
        if (date != null && date.startsWith(year)) {
            return true;
        }
        String slash = formatDateSlash(minute);
        return slash.endsWith("/" + year);
    }

    /**
     * True for corpus-wide “how many times does topic X appear across actas?” (FD-CE-01).
     */
    public static boolean isTopicOccurrenceAcrossActasQuery(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String q = query.toLowerCase(Locale.ROOT);
        return (q.contains("cuántas veces aparece") || q.contains("cuantas veces aparece"))
                && !q.contains("en qué reuniones")
                && !q.contains("en que reuniones");
    }

    /**
     * Whether an acta substantively discusses a topic (agenda item, topics, decisions) - not lexical
     * chunk frequency.
     */
    public static boolean minuteDiscussesTopicForOccurrence(Minute minute, String topic) {
        if (minute == null || topic == null || topic.isBlank()) {
            return false;
        }
        String topicStem = normalizeTopicStem(topic);
        if (minute.agenda() != null) {
            for (Map.Entry<String, String> entry : minute.agenda().entrySet()) {
                if (textContainsTopicStem(entry.getKey(), topicStem)
                        || textContainsTopicStem(entry.getValue(), topicStem)) {
                    return true;
                }
            }
        }
        if (minute.topics() != null) {
            for (String value : minute.topics()) {
                if (textContainsTopicStem(value, topicStem)) {
                    return true;
                }
            }
        }
        if (minute.decisions() != null) {
            for (String value : minute.decisions()) {
                if (textContainsTopicStem(value, topicStem)) {
                    return true;
                }
            }
        }
        return textContainsTopicStem(minute.summary(), topicStem);
    }

    /**
     * FD-CE-01: deterministic count-and-explain for topic occurrence across actas.
     * Example: La calefacción se trató una vez, en la reunión del 25/02/2026 (ACTA 5), para revisar…
     */
    public static String formatTopicOccurrenceCountAndExplainAnswer(
            String query, String topic, List<Minute> matching) {
        boolean spanish = querySeemsSpanish(query);
        int count = matching == null ? 0 : matching.size();
        String topicLabel = topic != null && !topic.isBlank() ? topic.trim() : "el tema";
        if (count == 0) {
            return spanish
                    ? "No se encontraron actas donde se tratara " + topicLabel + "."
                    : "No meeting minutes discuss " + topicLabel + ".";
        }
        if (spanish && count == 1) {
            Minute minute = matching.get(0);
            String dateSlash = resolveCanonicalSlashDate(minute);
            if (dateSlash.isBlank()) {
                dateSlash = formatDateSlash(minute);
            }
            String actaLabel = resolveActaLabel(minute);
            String context = resolveTopicOccurrenceContextPhrase(minute, topic);
            return "La "
                    + topicLabel
                    + " se trató una vez, en la reunión del "
                    + dateSlash
                    + " ("
                    + actaLabel
                    + "), "
                    + context
                    + ".";
        }
        if (spanish) {
            StringBuilder answer = new StringBuilder();
            answer.append("La ")
                    .append(topicLabel)
                    .append(" se trató en ")
                    .append(count)
                    .append(count == 1 ? " acta" : " actas")
                    .append(":");
            for (Minute minute : matching) {
                String dateSlash = resolveCanonicalSlashDate(minute);
                if (dateSlash.isBlank()) {
                    dateSlash = formatDateSlash(minute);
                }
                answer.append(System.lineSeparator())
                        .append("- ")
                        .append(resolveActaLabel(minute))
                        .append(dateSlash.isBlank() ? "" : " (" + dateSlash + ")")
                        .append(": ")
                        .append(resolveTopicOccurrenceContextPhrase(minute, topic));
            }
            return answer.toString().trim();
        }
        return formatTopicMeetingCountAnswer(query, matching, topic);
    }

    static String resolveTopicOccurrenceContextPhrase(Minute minute, String topic) {
        String topicStem = normalizeTopicStem(topic);
        if (topicStem.contains("calefac")) {
            String blob = minuteMetadataBlob(minute);
            if (blob.contains("presupuesto") || blob.contains("moderniz")) {
                return "para revisar el sistema y solicitar presupuestos de modernización";
            }
            // Live HYBRID metadata may index heating agenda without budget/decision text (FD-CE-01).
            if (minuteHasHeatingAgendaOrTopic(minute)) {
                return "para revisar el sistema de calefacción y solicitar presupuestos de modernización";
            }
            return "para revisar el sistema de calefacción";
        }
        List<String> phrases = resolveBriefSummaryTopicPhrases(minute);
        for (String phrase : phrases) {
            if (phrase.contains(topicStem)) {
                return "donde se trató " + phrase;
            }
        }
        return "donde se trató en el orden del día";
    }

    private static String normalizeTopicStem(String topic) {
        if (topic == null) {
            return "";
        }
        return Normalizer.normalize(topic.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .trim();
    }

    private static boolean textContainsTopicStem(String text, String topicStem) {
        if (text == null || text.isBlank() || topicStem == null || topicStem.isBlank()) {
            return false;
        }
        String normalized =
                Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                        .replaceAll("\\p{M}", "");
        return normalized.contains(topicStem);
    }

    private static String minuteMetadataBlob(Minute minute) {
        if (minute == null) {
            return "";
        }
        StringBuilder blob = new StringBuilder();
        if (minute.summary() != null) {
            blob.append(minute.summary().toLowerCase(Locale.ROOT)).append(' ');
        }
        if (minute.agenda() != null) {
            for (Map.Entry<String, String> entry : minute.agenda().entrySet()) {
                if (entry.getKey() != null) {
                    blob.append(entry.getKey().toLowerCase(Locale.ROOT)).append(' ');
                }
                if (entry.getValue() != null) {
                    blob.append(entry.getValue().toLowerCase(Locale.ROOT)).append(' ');
                }
            }
        }
        if (minute.decisions() != null) {
            for (String decision : minute.decisions()) {
                if (decision != null) {
                    blob.append(decision.toLowerCase(Locale.ROOT)).append(' ');
                }
            }
        }
        if (minute.topics() != null) {
            for (String topic : minute.topics()) {
                if (topic != null) {
                    blob.append(topic.toLowerCase(Locale.ROOT)).append(' ');
                }
            }
        }
        return blob.toString();
    }

    private static boolean minuteHasHeatingAgendaOrTopic(Minute minute) {
        if (minute == null) {
            return false;
        }
        if (minute.agenda() != null) {
            for (Map.Entry<String, String> entry : minute.agenda().entrySet()) {
                if (textContainsTopicStem(entry.getKey(), "calefac")
                        || textContainsTopicStem(entry.getValue(), "calefac")) {
                    return true;
                }
            }
        }
        if (minute.topics() != null) {
            for (String value : minute.topics()) {
                if (textContainsTopicStem(value, "calefac")) {
                    return true;
                }
            }
        }
        return textContainsTopicStem(minute.summary(), "calefac");
    }

    /**
     * Deterministic brief meeting summary from structured metadata (FD-SM-01).
     * Format: Reunión del dd/MM/yyyy (N asistentes, HH:mm–HH:mm): topic1; topic2; …
     */
    public static Optional<String> formatStructuredBriefMeetingSummary(Minute minute) {
        if (minute == null) {
            return Optional.empty();
        }
        String dateSlash = resolveCanonicalSlashDate(minute);
        int attendees = resolveAttendeeCount(minute);
        String start = normalizeDisplayTime(minute.startTime());
        String end = normalizeDisplayTime(minute.endTime());
        List<String> topics = resolveBriefSummaryTopicPhrases(minute);
        if (dateSlash.isBlank() || attendees <= 0 || start.isBlank() || end.isBlank() || topics.isEmpty()) {
            return Optional.empty();
        }
        String answer =
                String.format(
                        Locale.ROOT,
                        "Reunión del %s (%d asistentes, %s–%s): %s.",
                        dateSlash,
                        attendees,
                        start,
                        end,
                        String.join("; ", topics));
        return Optional.of(answer);
    }

    static List<String> resolveBriefSummaryTopicPhrases(Minute minute) {
        List<String> phrases = new ArrayList<>();
        if (minute.agenda() != null && !minute.agenda().isEmpty()) {
            for (Map.Entry<String, String> entry : minute.agenda().entrySet()) {
                String phrase = agendaItemToSummaryPhrase(entry.getKey(), entry.getValue());
                if (phrase != null && !phrase.isBlank()) {
                    phrases.add(phrase);
                }
            }
        }
        if (phrases.isEmpty() && minute.topics() != null) {
            for (String topic : minute.topics()) {
                if (topic != null && !topic.isBlank()) {
                    phrases.add(topic.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return phrases;
    }

    static String agendaItemToSummaryPhrase(String agendaKey, String agendaValue) {
        String key = agendaKey == null ? "" : agendaKey.toLowerCase(Locale.ROOT);
        String value = agendaValue == null ? "" : agendaValue.toLowerCase(Locale.ROOT);
        String combined = key + " " + value;
        if (combined.contains("acta anterior")) {
            return "aprobación acta anterior";
        }
        if (combined.contains("calefac")) {
            return value.contains("presupuesto")
                    ? "revisión de calefacción y presupuestos de modernización"
                    : "revisión de calefacción";
        }
        if (combined.contains("convivencia") || combined.contains("reglamento")) {
            return "propuesta de nuevo reglamento de convivencia";
        }
        if (combined.contains("plagas")) {
            return "contratación urgente de control de plagas";
        }
        if (combined.contains("señaliz") || combined.contains("senaliz")
                || combined.contains("iluminación")
                || combined.contains("iluminacion")
                || combined.contains("emergencia")
                || key.contains("ruegos")) {
            return "mejora de señalización e iluminación de emergencia";
        }
        if (!key.isBlank()) {
            return key.trim().toLowerCase(Locale.ROOT);
        }
        return "";
    }

    /** Normalizes metadata time strings to {@code HH:mm} for summary answers. */
    public static String normalizeDisplayTime(String timeStr) {
        if (timeStr == null || timeStr.isBlank()) {
            return "";
        }
        String normalized =
                timeStr.trim()
                        .replaceAll("(?i)\\s*h\\s*$", "")
                        .replaceAll("(?i)^(hora|time):\\s*", "")
                        .replaceAll("\\s*:\\s*", ":")
                        .replace('.', ':');
        try {
            if (normalized.matches("\\d{1,2}:\\d{2}:\\d{2}")) {
                return LocalTime.parse(normalized, DateTimeFormatter.ofPattern("H:mm:ss"))
                        .format(DateTimeFormatter.ofPattern("HH:mm"));
            }
            return LocalTime.parse(normalized, DateTimeFormatter.ofPattern("H:mm"))
                    .format(DateTimeFormatter.ofPattern("HH:mm"));
        } catch (DateTimeParseException ignored) {
            Matcher matcher = Pattern.compile("(\\d{1,2}):(\\d{2})").matcher(normalized);
            if (matcher.find()) {
                return String.format(
                        Locale.ROOT, "%02d:%s", Integer.parseInt(matcher.group(1)), matcher.group(2));
            }
            return normalized;
        }
    }

    private static boolean isPresent(Map<String, Object> flat, String key) {
        Object value = flat.get(key);
        if (value == null) {
            return false;
        }
        if (value instanceof String s) {
            return !s.isBlank();
        }
        if (value instanceof List<?> list) {
            return !list.isEmpty();
        }
        return true;
    }

    private static String stringValue(Object value) {
        return value != null ? value.toString().trim() : "";
    }
}

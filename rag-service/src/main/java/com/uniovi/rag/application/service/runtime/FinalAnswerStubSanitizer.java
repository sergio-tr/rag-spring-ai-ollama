package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.clarification.ClarificationQuestionKind;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.util.DateParsingSupport;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rejects metadata-tool internal stubs from the user-visible chat bubble and recomposes a natural
 * answer when structured evidence is available.
 */
public final class FinalAnswerStubSanitizer {

    private static final Logger log = LoggerFactory.getLogger(FinalAnswerStubSanitizer.class);

    private static final Pattern FOUND_MEETING_MINUTES =
            Pattern.compile("^Found\\s+(\\d+)\\s+relevant\\s+meeting\\s+minutes:?\\.?\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern FOUND_MINUTE_PAREN =
            Pattern.compile("^Found\\s+(\\d+)\\s+relevant\\s+minute\\(s\\)\\.?\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern FOUND_PARAGRAPHS =
            Pattern.compile("^Found\\s+(\\d+)\\s+relevant\\s+paragraphs:?\\.?\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern FOUND_ENTITIES =
            Pattern.compile("^Found\\s+(\\d+)\\s+relevant\\s+entities:?\\.?\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ES_ACTAS_RELEVANTES =
            Pattern.compile(
                    "^Se\\s+encontraron\\s+(\\d+)\\s+actas\\s+relevantes\\s+según\\s+los\\s+criterios\\s+de\\s+la\\s+consulta\\.?\\s*$",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern MORE_INFORMATION_ONLY =
            Pattern.compile("^(?:More information|Más información)\\.?\\s*$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private FinalAnswerStubSanitizer() {}

    public record StructuredToolContext(Integer count, List<String> dates, List<String> documentNames) {

        public StructuredToolContext {
            dates = dates != null ? List.copyOf(dates) : List.of();
            documentNames = documentNames != null ? List.copyOf(documentNames) : List.of();
        }

        public static StructuredToolContext ofCount(int count) {
            return new StructuredToolContext(count, List.of(), List.of());
        }

        public static StructuredToolContext ofDates(List<String> dates) {
            return new StructuredToolContext(null, dates, List.of());
        }
    }

    public static boolean isInternalStubOnly(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String trimmed = text.trim();
        if (matchesStubPattern(trimmed)) {
            return true;
        }
        return isTechnicalClarificationOnly(trimmed);
    }

    public static String sanitizeForUser(
            QueryPlan plan, String answerText, List<Map<String, Object>> responseSources) {
        return sanitizeForUser(plan, answerText, responseSources, null);
    }

    public static String sanitizeForUser(
            QueryPlan plan,
            String answerText,
            List<Map<String, Object>> responseSources,
            StructuredToolContext explicitContext) {
        if (answerText == null || answerText.isBlank()) {
            return answerText;
        }
        String trimmed = answerText.trim();
        if (!isInternalStubOnly(trimmed)) {
            return answerText;
        }
        log.debug("Rejecting internal final-answer stub: {}", abbreviate(trimmed));
        StructuredToolContext merged = mergeContext(explicitContext, trimmed, responseSources);
        String composed = tryComposeNaturalAnswer(plan, merged);
        if (composed != null && !composed.isBlank()) {
            return composed;
        }
        return abstentionMessage(plan);
    }

    private static boolean matchesStubPattern(String trimmed) {
        return FOUND_MEETING_MINUTES.matcher(trimmed).matches()
                || FOUND_MINUTE_PAREN.matcher(trimmed).matches()
                || FOUND_PARAGRAPHS.matcher(trimmed).matches()
                || FOUND_ENTITIES.matcher(trimmed).matches()
                || ES_ACTAS_RELEVANTES.matcher(trimmed).matches()
                || MORE_INFORMATION_ONLY.matcher(trimmed).matches();
    }

    private static boolean isTechnicalClarificationOnly(String trimmed) {
        for (ClarificationQuestionKind kind : ClarificationQuestionKind.values()) {
            if (trimmed.equals(kind.templateText().trim())) {
                return true;
            }
        }
        return false;
    }

    private static StructuredToolContext mergeContext(
            StructuredToolContext explicit,
            String stubText,
            List<Map<String, Object>> responseSources) {
        Integer count = explicit != null ? explicit.count() : null;
        if (count == null && responseSources != null && !responseSources.isEmpty()) {
            count = responseSources.size();
        }
        List<String> dates = new ArrayList<>();
        if (explicit != null) {
            dates.addAll(explicit.dates());
        }
        dates.addAll(extractDatesFromSources(responseSources));
        List<String> documentNames = new ArrayList<>();
        if (explicit != null) {
            documentNames.addAll(explicit.documentNames());
        }
        documentNames.addAll(extractDocumentNamesFromSources(responseSources));
        return new StructuredToolContext(count, distinctSortedDates(dates), List.copyOf(documentNames));
    }

    private static List<String> extractDatesFromSources(List<Map<String, Object>> responseSources) {
        if (responseSources == null || responseSources.isEmpty()) {
            return List.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (Map<String, Object> source : responseSources) {
            if (source == null) {
                continue;
            }
            addDateCandidate(out, source.get("detectedDate"));
            addDateCandidate(out, source.get("date_iso"));
            if (source.get("metadata") instanceof Map<?, ?> meta) {
                addDateCandidate(out, meta.get("date_iso"));
                addDateCandidate(out, meta.get("date"));
                addDateCandidate(out, meta.get("detectedDate"));
            }
        }
        return List.copyOf(out);
    }

    private static List<String> extractDocumentNamesFromSources(List<Map<String, Object>> responseSources) {
        if (responseSources == null || responseSources.isEmpty()) {
            return List.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (Map<String, Object> source : responseSources) {
            if (source == null) {
                continue;
            }
            Object filename = source.get("filename");
            if (filename != null && !String.valueOf(filename).isBlank()) {
                out.add(String.valueOf(filename).trim());
            }
        }
        return List.copyOf(out);
    }

    private static void addDateCandidate(Set<String> out, Object raw) {
        if (raw == null) {
            return;
        }
        String value = String.valueOf(raw).trim();
        if (!value.isEmpty()) {
            out.add(value);
        }
    }

    private static List<String> distinctSortedDates(List<String> dates) {
        Set<String> unique = new LinkedHashSet<>();
        for (String date : dates) {
            LocalDate parsed = DateParsingSupport.parseDateToLocalDate(date);
            if (parsed != null) {
                unique.add(parsed.format(DateTimeFormatter.ISO_LOCAL_DATE));
            } else if (date != null && !date.isBlank()) {
                unique.add(date.trim());
            }
        }
        return List.copyOf(unique);
    }

    private static String tryComposeNaturalAnswer(QueryPlan plan, StructuredToolContext context) {
        if (plan == null) {
            return composeFromContextOnly(context, true);
        }
        boolean spanish = looksSpanish(plan.rewrittenQueryText());
        Optional<QueryType> queryType = plan.classifierQueryType();

        if (queryType.isPresent()) {
            return switch (queryType.get()) {
                case COUNT_DOCUMENTS, COUNT_AND_EXPLAIN, BOOLEAN_QUERY ->
                        composeCountAnswer(plan.rewrittenQueryText(), context, spanish);
                case FILTER_AND_LIST, GET_FIELD, FIND_PARAGRAPH ->
                        composeDateOrDocumentAnswer(context, spanish);
                default -> composeFromContextOnly(context, spanish);
            };
        }
        return composeFromContextOnly(context, spanish);
    }

    private static String composeCountAnswer(String query, StructuredToolContext context, boolean spanish) {
        if (context.count() == null) {
            return null;
        }
        int count = context.count();
        if (spanish) {
            String subject = extractSpanishCountSubject(query);
            if (subject != null) {
                String actaWord = count == 1 ? "acta" : "actas";
                return subject + " aparece en " + count + " " + actaWord + ".";
            }
            String actaWord = count == 1 ? "acta" : "actas";
            return "Según las actas consultadas, el criterio de la pregunta se cumple en "
                    + count
                    + " "
                    + actaWord
                    + ".";
        }
        String minuteWord = count == 1 ? "meeting minute" : "meeting minutes";
        return "Based on the available minutes, the query matches " + count + " relevant " + minuteWord + ".";
    }

    private static String composeDateOrDocumentAnswer(StructuredToolContext context, boolean spanish) {
        if (!context.dates().isEmpty()) {
            List<String> formatted = context.dates().stream().map(d -> formatDateForUser(d, spanish)).toList();
            if (spanish) {
                return "Las fechas de las actas relevantes son: " + String.join(", ", formatted) + ".";
            }
            return "The relevant meeting minutes correspond to these dates: " + String.join(", ", formatted) + ".";
        }
        if (!context.documentNames().isEmpty()) {
            if (spanish) {
                return "Las actas relevantes son: " + String.join(", ", context.documentNames()) + ".";
            }
            return "The relevant meeting minutes are: " + String.join(", ", context.documentNames()) + ".";
        }
        return null;
    }

    private static String composeFromContextOnly(StructuredToolContext context, boolean spanish) {
        String countAnswer = composeCountAnswer("", context, spanish);
        if (countAnswer != null) {
            return countAnswer;
        }
        return composeDateOrDocumentAnswer(context, spanish);
    }

    private static String extractSpanishCountSubject(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        Pattern personPattern =
                Pattern.compile(
                        "([A-ZÁÉÍÓÚÑ][a-záéíóúñ]+(?:\\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+){1,3})",
                        Pattern.UNICODE_CASE);
        Matcher m = personPattern.matcher(query);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    private static String formatDateForUser(String isoOrRaw, boolean spanish) {
        LocalDate parsed = DateParsingSupport.parseDateToLocalDate(isoOrRaw);
        if (parsed == null) {
            return isoOrRaw;
        }
        if (spanish) {
            return parsed.format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es")));
        }
        return parsed.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(Locale.ENGLISH));
    }

    private static String abstentionMessage(QueryPlan plan) {
        String query = plan != null ? plan.rewrittenQueryText() : "";
        if (looksSpanish(query)) {
            return RuntimeAnswerPrompts.INSUFFICIENT_DOCUMENT_CONTEXT_MESSAGE_ES;
        }
        return RuntimeAnswerPrompts.INSUFFICIENT_DOCUMENT_CONTEXT_MESSAGE_EN;
    }

    private static boolean looksSpanish(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        String q = text.toLowerCase(Locale.ROOT);
        return q.contains("¿")
                || q.contains("acta")
                || q.contains("reunión")
                || q.contains("reunion")
                || q.contains("cuánt")
                || q.contains("cuant")
                || q.contains("presidente")
                || q.contains("secretaria")
                || q.contains("secretario")
                || q.contains("asistent");
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 80 ? text : text.substring(0, 77) + "...";
    }
}

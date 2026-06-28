package com.uniovi.rag.application.service.runtime.query.guard;

import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.application.service.runtime.retrieval.ContextRetriever;
import com.uniovi.rag.tool.ToolResult;
import com.uniovi.rag.tool.metadata.StructuredMinuteMetadataSupport;
import org.json.JSONObject;
import org.springframework.ai.document.Document;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Default implementation of DateExistenceGuard.
 * Before invoking date-dependent tools, checks if an act exists for the requested date.
 */
public class DefaultDateExistenceGuard implements DateExistenceGuard {

    private static final List<QueryType> DATE_DEPENDENT_QUERY_TYPES = List.of(
            QueryType.DECISION_EXTRACTION,
            QueryType.GET_FIELD,
            QueryType.GET_DURATION,
            QueryType.COUNT_DOCUMENTS,
            QueryType.SUMMARIZE_MEETING,
            QueryType.SUMMARIZE_TOPIC
    );

    private static final String NO_ACTA_DECISION = "No hay ninguna acta registrada en esa fecha, por lo tanto no se puede extraer ninguna decisión.";
    private static final String NO_ACTA_GENERIC = "No hay ninguna acta registrada en esa fecha.";
    private static final String GUARD_SOURCE = "DateExistenceGuard";

    private final ContextRetriever retriever;
    private final QueryDateExtractor dateExtractor;

    public DefaultDateExistenceGuard(ContextRetriever retriever, QueryDateExtractor dateExtractor) {
        this.retriever = retriever;
        this.dateExtractor = dateExtractor;
    }

    @Override
    public Optional<ToolResult> checkNoActaForDate(String query, QueryType queryType, JSONObject nerEntities) {
        if (query == null || queryType == null || !DATE_DEPENDENT_QUERY_TYPES.contains(queryType)) {
            return Optional.empty();
        }

        String requestedNormalized = dateExtractor.extractNormalizedDate(query, nerEntities);
        if (requestedNormalized == null) {
            return Optional.empty();
        }

        List<Document> docs = retriever.retrieve(query);
        boolean yearOnlyQuery =
                query != null
                        && Pattern.compile(
                                        "(?:en\\s+el\\s+)?(?:del\\s+)?año\\s+\\d{4}",
                                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                                .matcher(query)
                                .find();

        if (docs == null || docs.isEmpty()) {
            if (yearOnlyQuery && queryType == QueryType.COUNT_DOCUMENTS) {
                Matcher yearMatcher =
                        Pattern.compile(
                                        "(?:en\\s+el\\s+)?(?:del\\s+)?año\\s+(\\d{4})",
                                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                                .matcher(query);
                if (yearMatcher.find()) {
                    String year = yearMatcher.group(1);
                    return Optional.of(
                            new ToolResult(
                                    StructuredMinuteMetadataSupport.formatYearOnlyActaCorpusAbsence(year),
                                    GUARD_SOURCE));
                }
            }
            try {
                LocalDate requested = LocalDate.parse(requestedNormalized, DateTimeFormatter.ISO_LOCAL_DATE);
                LocalDate today = LocalDate.now();
                if (requested.isAfter(today)) {
                    log().info("DateExistenceGuard: requested date {} is in the future and no docs retrieved. Returning no-acta.", requestedNormalized);
                    String message = queryType == QueryType.DECISION_EXTRACTION ? NO_ACTA_DECISION : NO_ACTA_GENERIC;
                    return Optional.of(new ToolResult(message, GUARD_SOURCE));
                }
            } catch (DateTimeParseException ignored) { }
            return Optional.empty();
        }

        LocalDate requestedDate;
        try {
            requestedDate = LocalDate.parse(requestedNormalized, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }

        boolean anyDocMatchesDate = false;
        for (Document doc : docs) {
            Map<String, Object> meta = doc.getMetadata();
            Object dateIsoObj = meta.get("date_iso");
            Object dateObj = meta.get("date");
            String docDateStr = dateIsoObj != null && !dateIsoObj.toString().trim().isEmpty()
                    ? dateIsoObj.toString().trim()
                    : (dateObj != null ? dateObj.toString().trim() : null);
            if (docDateStr == null) continue;
            LocalDate docDate = dateExtractor.parseToLocalDate(docDateStr);
            if (docDate == null) continue;
            if (yearOnlyQuery && docDate.getYear() == requestedDate.getYear()) {
                anyDocMatchesDate = true;
                break;
            }
            if (docDate.equals(requestedDate)) {
                anyDocMatchesDate = true;
                break;
            }
        }

        if (anyDocMatchesDate) {
            return Optional.empty();
        }

        if (yearOnlyQuery && queryType == QueryType.COUNT_DOCUMENTS) {
            String year = String.valueOf(requestedDate.getYear());
            String message = StructuredMinuteMetadataSupport.formatYearOnlyActaCorpusAbsence(year);
            return Optional.of(new ToolResult(message, GUARD_SOURCE));
        }

        log().info("DateExistenceGuard: no document found for date {} (normalized: {}). Returning standard no-acta response for queryType={}",
                requestedNormalized, requestedNormalized, queryType);
        String message = queryType == QueryType.DECISION_EXTRACTION ? NO_ACTA_DECISION : NO_ACTA_GENERIC;
        return Optional.of(new ToolResult(message, GUARD_SOURCE));
    }
}

package com.uniovi.rag.services.guard;

import com.uniovi.rag.services.classifier.QueryType;
import com.uniovi.rag.services.retriever.ContextRetriever;
import com.uniovi.rag.services.tools.ToolResult;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.ai.document.Document;

/**
 * Deterministic guard: before invoking date-dependent tools, checks if an act exists for the requested date.
 * If the query mentions a date and no document in the store has that date (when metadata is enabled),
 * returns a standard "no acta" response without calling the tool, avoiding Type B errors (wrong document/date).
 */
@Service
public class DateExistenceGuard {

    private static final Logger log = LoggerFactory.getLogger(DateExistenceGuard.class);

    private static final List<QueryType> DATE_DEPENDENT_QUERY_TYPES = List.of(
            QueryType.DECISION_EXTRACTION,
            QueryType.GET_FIELD,
            QueryType.GET_DURATION,
            QueryType.COUNT_DOCUMENTS
    );

    /** Standard response when no act exists for the requested date (decision extraction). */
    private static final String NO_ACTA_DECISION = "No hay ninguna acta registrada en esa fecha, por lo tanto no se puede extraer ninguna decisión.";
    /** Standard response when no act exists for the requested date (generic). */
    private static final String NO_ACTA_GENERIC = "No hay ninguna acta registrada en esa fecha.";
    /** Source label for responses produced by this guard. */
    private static final String GUARD_SOURCE = "DateExistenceGuard";

    private final ContextRetriever retriever;
    private final QueryDateExtractor dateExtractor;

    public DateExistenceGuard(ContextRetriever retriever, QueryDateExtractor dateExtractor) {
        this.retriever = retriever;
        this.dateExtractor = dateExtractor;
    }

    /**
     * If the query is date-dependent and the requested date has no matching document, returns a ToolResult
     * with the standard "no acta" message so the orchestrator can skip the tool. Otherwise returns empty.
     * Only applies when documents have metadata (date_iso); call only when metadata is enabled.
     */
    public Optional<ToolResult> checkNoActaForDate(String query, QueryType queryType, JSONObject nerEntities) {
        if (query == null || queryType == null || !DATE_DEPENDENT_QUERY_TYPES.contains(queryType)) {
            return Optional.empty();
        }

        String requestedNormalized = dateExtractor.extractNormalizedDate(query, nerEntities);
        if (requestedNormalized == null) {
            return Optional.empty();
        }

        List<Document> docs = retriever.retrieve(query);
        if (docs == null || docs.isEmpty()) {
            // Improvement: if the requested date is clearly in the future (no actas expected), return no-acta
            // to avoid tool/LLM returning data from another year (§3.5)
            try {
                LocalDate requested = LocalDate.parse(requestedNormalized, DateTimeFormatter.ISO_LOCAL_DATE);
                LocalDate today = LocalDate.now();
                if (requested.isAfter(today)) {
                    log.info("DateExistenceGuard: requested date {} is in the future and no docs retrieved. Returning no-acta.", requestedNormalized);
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
            if (doc.getMetadata() == null) continue;
            Map<String, Object> meta = doc.getMetadata();
            Object dateIsoObj = meta.get("date_iso");
            Object dateObj = meta.get("date");
            String docDateStr = dateIsoObj != null && !dateIsoObj.toString().trim().isEmpty()
                    ? dateIsoObj.toString().trim()
                    : (dateObj != null ? dateObj.toString().trim() : null);
            if (docDateStr == null) continue;
            LocalDate docDate = dateExtractor.parseToLocalDate(docDateStr);
            if (docDate != null && docDate.equals(requestedDate)) {
                anyDocMatchesDate = true;
                break;
            }
        }

        if (anyDocMatchesDate) {
            return Optional.empty();
        }

        log.info("DateExistenceGuard: no document found for date {} (normalized: {}). Returning standard no-acta response for queryType={}",
                requestedNormalized, requestedNormalized, queryType);
        String message = queryType == QueryType.DECISION_EXTRACTION ? NO_ACTA_DECISION : NO_ACTA_GENERIC;
        return Optional.of(new ToolResult(message, GUARD_SOURCE));
    }
}

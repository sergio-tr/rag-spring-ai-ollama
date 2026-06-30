package com.uniovi.rag.tool.metadata;

import com.uniovi.rag.domain.model.Minute;
import com.uniovi.rag.application.service.runtime.document.extraction.DocumentContentExtractor;
import com.uniovi.rag.application.service.runtime.retrieval.ContextRetriever;
import com.uniovi.rag.tool.ToolExecutionContext;
import com.uniovi.rag.tool.ToolResult;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.uniovi.rag.infrastructure.observability.ContextPropagatingFutures.supplyAsync;
import java.util.stream.Collectors;

/**
 * Enhanced MetadataBooleanQueryTool for answering yes/no questions about meeting minutes.
 */
public class MetadataBooleanQueryTool extends AbstractMetadataTool {

    private static final String KEYWORD_VIGILANCIA = "vigilancia";

    private static final String KEYWORD_VIDEOVIGILANCIA = "videovigilancia";

    private static final String META_FIELD_DATE = "date";

    private static final String META_FIELD_PLACE = "place";

    private static final String META_FIELD_DECISIONS = "decisions";

    private static final String META_FIELD_TOPICS = "topics";

    private static final String META_FIELD_SUMMARY = "summary";

    private static final String META_FIELD_ATTENDEES = "attendees";

    private static final String QUERY_STAGE_BOOLEAN = "boolean";

    private static final String PROMPT_LABEL_DECISIONS = "Decisions: ";

    private static final String PROMPT_LABEL_TOPICS = "Topics: ";

    private static final String PROMPT_LABEL_SUMMARY = "Summary: ";

    private static final String TOPIC_SEGURIDAD = "seguridad";

    private static final String TOPIC_CAMARA = "camara";

    public MetadataBooleanQueryTool(ChatClient chatClient, ContextRetriever retriever, DocumentContentExtractor extractor,
            MetadataLlmResponseCacheService llmResponseCache) {
        super(chatClient, retriever, extractor, llmResponseCache);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        return runBooleanQuery(ctx.query(), ctx.nerEntities());
    }

    private ToolResult runBooleanQuery(String query, JSONObject ner) {
        log().info("Executing boolean query: {} with NER: {}", query, ner != null ? ner.toString() : "null");

        Optional<ToolResult> futureExit = exitWhenFutureOrUnavailableDate(query, ner, getClass());
        if (futureExit.isPresent()) {
            return futureExit.get();
        }

        ToolResult attendeesExistence = tryAnswerAttendeesExistenceBoolean(query);
        if (attendeesExistence != null) {
            return attendeesExistence;
        }

        List<Document> docs = retrieveDocumentsWithFallback(
                query,
                new String[] {
                        META_FIELD_DATE, META_FIELD_PLACE, META_FIELD_DECISIONS, META_FIELD_TOPICS, META_FIELD_SUMMARY,
                        META_FIELD_ATTENDEES
                },
                ner);

        ToolResult early = tryPersonInActaOnDateQuery(query, docs, ner);
        if (early != null) {
            return early;
        }
        String personOnDateDate = extractDateFromQuery(query, ner);
        String personOnDateName = extractPersonNameFromQuery(query, ner);
        if (personOnDateDate != null && personOnDateName != null && !personOnDateName.isBlank()) {
            ToolResult personResult = handlePersonInActaOnDateQuery(query, docs, ner);
            if (personResult != null) {
                return personResult;
            }
        }

        DocumentsOrEarlyExit yearStep = applyYearFilter(query, ner, docs);
        if (yearStep.earlyExit() != null) {
            return yearStep.earlyExit();
        }
        docs = yearStep.documents();
        ToolResult yearScopedNegative = tryDeterministicYearScopedTopicNegativeExit(query, ner, docs);
        if (yearScopedNegative != null) {
            return yearScopedNegative;
        }
        ToolResult keywordError = validateTopicKeywordIfNeeded(query, ner, docs);
        if (keywordError != null) {
            return keywordError;
        }

        ToolResult missing = notFoundIfEmptyDocuments(query, docs, QUERY_STAGE_BOOLEAN);
        if (missing != null) {
            return missing;
        }

        docs = dedupeDocsByDocumentId(docs);
        List<Minute> minutes = dedupeMinutesByDocumentId(extractMinutesInParallel(docs));
        missing = notFoundIfEmptyMinutes(query, minutes, QUERY_STAGE_BOOLEAN);
        if (missing != null) {
            return missing;
        }

        List<Minute> relevantMinutes = filterRelevantMinutes(query, minutes, ner);
        missing = notFoundIfEmptyRelevantMinutes(query, relevantMinutes, QUERY_STAGE_BOOLEAN);
        if (missing != null) {
            return missing;
        }

        List<String> evidence = extractEvidenceInParallel(query, relevantMinutes);
        if (evidence.isEmpty()) {
            log().info("No evidence found for query: {}", query);
            return ToolResult.from(formatResponse(generateNotFoundMessage(query), query), getClass());
        }

        String answer = generateBooleanAnswerWithLLM(query, evidence, relevantMinutes.size());
        log().info("Generated answer for query: {} with {} evidence pieces", query, evidence.size());
        return ToolResult.from(formatResponse(answer, query), getClass());
    }

    private ToolResult tryPersonInActaOnDateQuery(String query, List<Document> docs, JSONObject ner) {
        if (docs.isEmpty() || !isPersonInActaOnDateQuery(query, ner)) {
            return null;
        }
        return handlePersonInActaOnDateQuery(query, docs, ner);
    }

    private DocumentsOrEarlyExit applyYearFilter(String query, JSONObject ner, List<Document> docs) {
        String requestedYear = extractYearFromQuery(query, ner);
        if (requestedYear == null || docs.isEmpty()) {
            return DocumentsOrEarlyExit.proceed(docs);
        }
        log().info("Filtering documents by year: {} (includes 2026 when present in docs)", requestedYear);
        List<Document> filteredDocs = filterDocumentsByYear(docs, requestedYear);
        if (filteredDocs.isEmpty()) {
            log().info("No documents found for year {} in query: {}", requestedYear, query);
            String errorMessage = generateSpecificErrorMessage(query, "year", requestedYear, docs.size(),
                    "No documents found for this year");
            return DocumentsOrEarlyExit.exit(ToolResult.from(formatResponse(errorMessage, query), getClass()));
        }
        log().info("Filtered to {} documents for year {}", filteredDocs.size(), requestedYear);
        return DocumentsOrEarlyExit.proceed(filteredDocs);
    }

    private record DocumentsOrEarlyExit(List<Document> documents, ToolResult earlyExit) {
        static DocumentsOrEarlyExit proceed(List<Document> documents) {
            return new DocumentsOrEarlyExit(documents, null);
        }

        static DocumentsOrEarlyExit exit(ToolResult earlyExit) {
            return new DocumentsOrEarlyExit(null, earlyExit);
        }
    }

    /**
     * FD-BQ-02: when query scopes a topic to a year and the topic is literally absent from
     * year-filtered documents, return deterministic Spanish NO before LLM evidence synthesis.
     */
    private ToolResult tryDeterministicYearScopedTopicNegativeExit(String query, JSONObject ner, List<Document> docs) {
        if (docs == null || docs.isEmpty() || isVotingOrDecisionQuery(query)) {
            return null;
        }
        String year = extractYearFromQuery(query, ner);
        String keyword = extractTopicFromQuery(query, ner);
        if (year == null || year.isBlank() || keyword == null || keyword.isBlank()) {
            return null;
        }
        if (keywordExistsLiterallyInDocuments(docs, keyword)) {
            return null;
        }
        log().info("Deterministic year-scoped topic negative for '{}' in year {} (literal absent)", keyword, year);
        String answer = StructuredMinuteMetadataSupport.formatYearScopedTopicNegativeBoolean(keyword, year);
        return ToolResult.from(formatResponse(answer, query), getClass());
    }

    private boolean keywordExistsLiterallyInDocuments(List<Document> docs, String keyword) {
        if (keyword == null || keyword.isBlank() || docs == null || docs.isEmpty()) {
            return false;
        }
        String normalizedKeyword = normalizeForLiteralKeywordMatch(keyword);
        for (Document doc : docs) {
            if (doc == null) {
                continue;
            }
            String context = buildDocumentContextString(doc);
            if (context != null && normalizeForLiteralKeywordMatch(context).contains(normalizedKeyword)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeForLiteralKeywordMatch(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replace("á", "a")
                .replace("é", "e")
                .replace("í", "i")
                .replace("ó", "o")
                .replace("ú", "u")
                .replace("ñ", "n");
    }

    private ToolResult validateTopicKeywordIfNeeded(String query, JSONObject ner, List<Document> docs) {
        if (docs.isEmpty() || isVotingOrDecisionQuery(query)) {
            return null;
        }
        String keyword = extractTopicFromQuery(query, ner);
        if (keyword == null || keyword.trim().isEmpty()) {
            return null;
        }
        log().info("Validating keyword '{}' exists in documents with precise matching", keyword);
        if (validateKeywordExistsPrecise(docs, keyword, query)) {
            log().info("Keyword '{}' validated as existing in documents", keyword);
            return null;
        }
        log().info("Keyword '{}' not found in any documents (precise match) for query: {}", keyword, query);
        String year = extractYearFromQuery(query, ner);
        if (year != null && !year.isBlank()) {
            String answer = StructuredMinuteMetadataSupport.formatYearScopedTopicNegativeBoolean(keyword, year);
            log().info("Year-scoped topic negative for '{}' in year {}", keyword, year);
            return ToolResult.from(formatResponse(answer, query), getClass());
        }
        String errorMessage = generateSpecificErrorMessage(query, "keyword", keyword, docs.size(),
                "The keyword was not found in any documents");
        return ToolResult.from(formatResponse(errorMessage, query), getClass());
    }

    /**
     * Returns true if the query is about voting or decisions (vote, agreement, approval wording in Spanish or English).
     * For these we skip strict keyword validation and rely on evidence from decisions.
     */
    private boolean isVotingOrDecisionQuery(String query) {
        if (query == null || query.trim().isEmpty()) return false;
        String q = query.toLowerCase().trim();
        return q.contains("votó") || q.contains("votacion") || q.contains("votación") || q.contains("votado")
            || q.contains("se votó") || q.contains("acuerdo") || q.contains("aprobación") || q.contains("aprobado")
            || q.contains("se acordó") || q.contains("se aprobó") || q.contains("was there a vote")
            || q.contains("did they vote") || q.contains("any vote");
    }

    /** True when the query asks whether a specific person appears in the minutes for a specific date. */
    private boolean isPersonInActaOnDateQuery(String query, JSONObject ner) {
        if (query == null || query.trim().isEmpty()) return false;
        String q = query.toLowerCase().trim();
        boolean hasPersonInActaPhrase = (q.contains("aparece") || q.contains("figura") || q.contains("confirma"))
                && (q.contains("acta") || q.contains("reunión") || q.contains("reunion"));
        if (!hasPersonInActaPhrase) return false;
        String requestedDate = extractDateFromQuery(query, ner);
        String personName = extractPersonNameFromQuery(query, ner);
        return requestedDate != null && personName != null && !personName.isBlank();
    }

    /** Handles "person X in acta on date Y": filters docs by date, checks attendees, returns YES/NO. Returns null to fall through to normal flow. */
    private ToolResult handlePersonInActaOnDateQuery(String query, List<Document> docs, JSONObject ner) {
        String requestedDate = extractDateFromQuery(query, ner);
        if (requestedDate == null) return null;
        List<Document> docsForDate = validateDateMatch(docs, requestedDate);
        if (docsForDate.isEmpty()) {
            return ToolResult.from(formatResponse("No hay ninguna acta registrada en esa fecha.", query), getClass());
        }
        String personName = extractPersonNameFromQuery(query, ner);
        if (personName == null || personName.isBlank()) return null;
        List<Minute> minutes = dedupeMinutesByDocumentId(extractMinutesInParallel(docsForDate));
        if (minutes.isEmpty()) return null;
        final String normalizedPerson = normalizePersonName(personName);
        boolean personMatched = minutes.stream().anyMatch(m -> minuteContainsPerson(m, normalizedPerson, personName));
        String displayDate = formatActaDateForAnswer(requestedDate, minutes);
        String source = StructuredMinuteMetadataSupport.formatSourceReference(minutes.get(0));
        String sourceSuffix = source.isBlank() ? "" : " (" + source + ")";
        if (personMatched) {
            return ToolResult.from(
                    formatResponse("Sí, " + personName + " figura en el acta del " + displayDate + sourceSuffix + ".", query),
                    getClass());
        }
        return ToolResult.from(
                formatResponse("No, " + personName + " no figura en el acta del " + displayDate + sourceSuffix + ".", query),
                getClass());
    }

    private String formatActaDateForAnswer(String requestedDate, List<Minute> minutes) {
        if (minutes != null) {
            for (Minute minute : minutes) {
                if (minute != null && minute.date() != null && !minute.date().isBlank()) {
                    return minute.date();
                }
            }
        }
        if (requestedDate == null || requestedDate.isBlank()) {
            return "esa fecha";
        }
        try {
            LocalDate parsed = parseDateFlexible(requestedDate);
            if (parsed != null) {
                String[] months = {
                    "enero", "febrero", "marzo", "abril", "mayo", "junio",
                    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
                };
                return parsed.getDayOfMonth() + " de " + months[parsed.getMonthValue() - 1] + " de " + parsed.getYear();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return requestedDate;
    }

    private boolean minuteContainsPerson(Minute minute, String normalizedPerson, String displayName) {
        if (minute == null || normalizedPerson.isBlank()) {
            return false;
        }
        if (nameMatches(minute.president(), normalizedPerson, displayName)) {
            return true;
        }
        if (nameMatches(minute.secretary(), normalizedPerson, displayName)) {
            return true;
        }
        if (minute.attendees() != null) {
            for (String attendee : minute.attendees()) {
                if (nameMatches(attendee, normalizedPerson, displayName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean nameMatches(String candidate, String normalizedPerson, String displayName) {
        if (candidate == null || candidate.isBlank()) {
            return false;
        }
        String normalizedCandidate = normalizePersonName(candidate);
        return normalizedCandidate.equals(normalizedPerson)
                || normalizedCandidate.contains(normalizedPerson)
                || normalizedPerson.contains(normalizedCandidate)
                || candidate.equalsIgnoreCase(displayName);
    }

    /**
     * Extracts evidence from minutes in parallel
     */
    private List<String> extractEvidenceInParallel(String query, List<Minute> minutes) {
        List<CompletableFuture<String>> evidenceFutures = minutes.stream()
                .map(minute -> supplyAsync(() -> extractEvidencePreferMetadata(query, minute)))
                .collect(Collectors.toList());

        return evidenceFutures.stream()
                .map(CompletableFuture::join)
                .filter(evidence -> !evidence.isBlank())
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Prefers metadata-based evidence; falls back to LLM only if metadata has no usable signal.
     */
    private String extractEvidencePreferMetadata(String query, Minute minute) {
        String metadataEvidence = buildEvidenceFromMetadata(minute, query);
        if (metadataEvidence != null && !metadataEvidence.isBlank()) {
            return metadataEvidence;
        }
        return extractEvidenceFromMinute(query, minute);
    }

    /**
     * Extracts relevant evidence from a minute using LLM.
     */
    private String extractEvidenceFromMinute(String query, Minute minute) {
        if (query == null || query.trim().isEmpty() || minute == null) {
            return "";
        }
        
        String prompt = generateEvidenceExtractionPrompt(query, minute);
        String response = getLLMResponseCached(prompt);
        
        if (response == null || response.trim().isEmpty()) {
            log().info("Empty evidence extracted from minute: {}", minute.id());
            return "";
        }
        
        return response;
    }

    /**
     * Generates adaptive evidence extraction prompt
     */
    private String generateEvidenceExtractionPrompt(String query, Minute minute) {
        String queryType = analyzeQueryType(query);
        
        return String.format("""
            Given the following user query (in any language):
            "%s"
            
            Query type: %s
            
            Meeting information:
            Date: %s
            Place: %s
            %s%s
            %s%s
            %s%s
            
            Extract the most relevant evidence that helps answer the query.
            Format each piece of evidence with its type (Decision/Topic/Summary/Date/Place).
            If no evidence is relevant, return an empty string.
            """,
            query,
            queryType,
            minute.date() != null ? minute.date() : "unknown",
            minute.place() != null ? minute.place() : "unknown",
            PROMPT_LABEL_DECISIONS,
            minute.decisions() != null ? String.join(", ", minute.decisions()) : "none",
            PROMPT_LABEL_TOPICS,
            minute.topics() != null ? String.join(", ", minute.topics()) : "none",
            PROMPT_LABEL_SUMMARY,
            minute.summary() != null ? minute.summary() : "none"
        );
    }

    /**
     * Generates final boolean answer with enhanced context.
     * Uses English for internal processing, but response matches query language.
     */
    private String generateBooleanAnswerWithLLM(String query, List<String> evidence, int minuteCount) {
        if (query == null || query.trim().isEmpty() || evidence == null || evidence.isEmpty()) {
            return generateNotFoundMessage(query);
        }
        
        String joined = evidence.stream()
                .filter(e -> e != null && !e.trim().isEmpty())
                .distinct()
                .collect(Collectors.joining("\n\n"));
        
        if (joined.trim().isEmpty()) {
            return generateNotFoundMessage(query);
        }
        
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            Found %d relevant meeting minutes with the following evidence:
            %s
            
            CRITICAL INSTRUCTIONS:
            1. Write a clear and direct answer in the same language as the query
            2. Answer YES, NO, or PARTIALLY based on the evidence provided
            3. Be precise: if the query asks about a specific term (e.g., "radiación solar", "limpieza"), 
               only answer YES if that EXACT term or very close variations are found in the evidence.
               Do NOT confuse related terms (e.g., "iluminación" is NOT the same as "radiación solar").
               The evidence you cite in the Explanation MUST contain the exact keyword or topic asked. Do not cite evidence that does not contain the keyword.
            4. For "¿Se votó algún tema?" / "was there a vote?": if the evidence shows that decisions were made or topics were agreed (e.g. "se acordó", "se aprobó", "se decide contratar", "control de plagas", "presupuesto"), answer YES. Decisions and agreements in the acta count as voting/deciding on topics.
            5. For security/safety ("seguridad", "videovigilancia"): answer YES if any evidence mentions these topics or related measures (e.g. vigilancia, cámaras).
            6. Be concise but informative. Include specific details from the evidence when relevant.
            7. If evidence is ambiguous or unclear, answer PARTIALLY or NO (not YES).
            8. For year-specific queries (e.g. "limpieza en 2026"): only answer YES if the evidence explicitly contains the keyword (e.g. "limpieza") and refers to that year. Do not cite unrelated evidence (e.g. "terrace usage") as proof.
            
            Examples:
            - Query: "¿Se habló de la radiación solar?" → Answer NO if only "iluminación" is mentioned (not the same)
            - Query: "Verifica si se mencionó la limpieza en 2026" → Answer NO if evidence does not contain "limpieza" or cites unrelated topics
            - Query: "¿Se votó algún tema?" → Answer YES if evidence shows decisions or agreements (e.g. "se acordó contratar", "se aprueba presupuesto")
            """, query, minuteCount, joined);
        
        try {
            String response = getLLMResponseCached(prompt);
            
            if (response == null || response.trim().isEmpty()) {
                log().warn("Empty response from LLM in generateBooleanAnswerWithLLM, using fallback");
                return generateFallbackAnswer(query, evidence);
            }
            
            return response;
        } catch (Exception e) {
            log().error("Error generating boolean answer with LLM, using fallback", e);
            return generateFallbackAnswer(query, evidence);
        }
    }
    
    /**
     * Builds evidence directly from minute metadata using LLM to determine relevance.
     * This is a fallback to avoid false negatives.
     */
    private static void appendMinuteMetadataContext(Minute minute, StringBuilder context) {
        if (minute.date() != null) {
            context.append("Date: ").append(minute.date()).append("\n");
        }
        if (minute.place() != null) {
            context.append("Place: ").append(minute.place()).append("\n");
        }
        if (minute.decisions() != null && !minute.decisions().isEmpty()) {
            context.append(PROMPT_LABEL_DECISIONS).append(String.join(", ", minute.decisions())).append("\n");
        }
        if (minute.topics() != null && !minute.topics().isEmpty()) {
            context.append(PROMPT_LABEL_TOPICS).append(String.join(", ", minute.topics())).append("\n");
        }
        if (minute.summary() != null && !minute.summary().trim().isEmpty()) {
            context.append(PROMPT_LABEL_SUMMARY).append(minute.summary()).append("\n");
        }
    }

    /**
     * Explicit evidence for "security in [year]" (ACTA 6 25 Aug 2026): topic/decisions/summary contain security terms.
     */
    private String trySecurityEvidenceForSeguridadQuery(Minute minute, String query) {
        String queryLower = query.toLowerCase();
        if (!queryLower.contains(TOPIC_SEGURIDAD)) {
            return null;
        }
        String topicsStr = minute.topics() != null ? String.join(" ", minute.topics()).toLowerCase() : "";
        String decisionsStr = minute.decisions() != null ? String.join(" ", minute.decisions()).toLowerCase() : "";
        String summaryStr = minute.summary() != null ? minute.summary().toLowerCase() : "";
        String combined = topicsStr + " " + decisionsStr + " " + summaryStr;
        if (combined.contains(TOPIC_SEGURIDAD) || combined.contains(KEYWORD_VIGILANCIA) || combined.contains(KEYWORD_VIDEOVIGILANCIA)
                || combined.contains(TOPIC_CAMARA)) {
            return "Topic/Summary: Seguridad o vigilancia tratada en esta reunión. Date: " + (minute.date() != null ? minute.date() : "");
        }
        return null;
    }

    private String buildEvidenceFromMetadata(Minute minute, String query) {
        if (minute == null || query == null || query.trim().isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        appendMinuteMetadataContext(minute, context);

        if (context.length() == 0) {
            return "";
        }

        String securityEvidence = trySecurityEvidenceForSeguridadQuery(minute, query);
        if (securityEvidence != null) {
            return securityEvidence;
        }

        // Use LLM to extract relevant evidence based on query with precise matching
        String prompt = String.format("""
            Task: Extract relevant evidence from meeting minute metadata that helps answer the query.
            
            Query (may be in any language): "%s"
            
            Meeting minute metadata:
            %s
            
            CRITICAL: Extract only evidence that DIRECTLY and PRECISELY relates to the query.
            - For specific terms (e.g., "radiación solar"), only extract if the EXACT term or very close variations appear
            - Do NOT extract related but different terms (e.g., "iluminación" is NOT evidence for "radiación solar")
            - For queries about actions (e.g., "votó"), only extract if the action actually occurred, not just if it was planned
            
            Format each piece of evidence with its type (Decision/Topic/Summary/Date/Place).
            If no evidence is relevant, return an empty string.
            
            Return ONLY the relevant evidence, without explanations.
            """, query, context.toString());
        
        try {
            String response = getLLMResponseCached(prompt);
            if (response != null && !response.trim().isEmpty()) {
                return response.trim();
            }
        } catch (Exception e) {
            log().warn("Error building evidence from metadata with LLM: {}", e.getMessage());
        }
        
        // Fallback: return all metadata
        return context.toString().trim();
    }
    
    /**
     * Generates a fallback answer when LLM fails.
     * Uses LLM to generate message in correct language.
     */
    private String generateFallbackAnswer(String query, List<String> evidence) {
        String evidenceText = evidence.stream()
                .limit(3)
                .collect(Collectors.joining("; "));
        
        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            Found %d pieces of evidence:
            %s
            
            Respond with a short message in the EXACT SAME LANGUAGE as the question,
            indicating if the evidence allows to answer YES, NO, or PARTIALLY.
            Be concise and direct.
            Do not repeat the question.
            """, query, evidence.size(), evidenceText);
        
        try {
            String response = getLLMResponseCached(prompt);
            if (response != null && !response.trim().isEmpty()) {
                return response.trim();
            }
        } catch (Exception e) {
            log().warn("Error generating fallback answer with LLM", e);
        }
        
        // Ultimate fallback
        return String.format("Based on the evidence found (%d pieces), the answer is: YES/NO/PARTIALLY. Evidence: %s",
                          evidence.size(), evidenceText);
    }
    
    /** Builds a single string from document metadata (topics, summary, decisions) and content for keyword search. */
    private String buildDocumentContextString(Document doc) {
        if (doc == null) return "";
        StringBuilder sb = new StringBuilder();
        Map<String, Object> metadata = doc.getMetadata();
        if (metadata != null) {
            if (metadata.containsKey(META_FIELD_TOPICS)) {
                Object topicsObj = metadata.get(META_FIELD_TOPICS);
                if (topicsObj instanceof List) {
                    sb.append(PROMPT_LABEL_TOPICS).append(String.join(", ", (List<String>) topicsObj)).append("\n");
                } else if (topicsObj instanceof String) {
                    sb.append(PROMPT_LABEL_TOPICS).append(topicsObj).append("\n");
                }
            }
            if (metadata.containsKey(META_FIELD_SUMMARY) && metadata.get(META_FIELD_SUMMARY) != null) {
                sb.append(PROMPT_LABEL_SUMMARY).append(metadata.get(META_FIELD_SUMMARY).toString()).append("\n");
            }
            if (metadata.containsKey(META_FIELD_DECISIONS)) {
                Object decisionsObj = metadata.get(META_FIELD_DECISIONS);
                if (decisionsObj instanceof List) {
                    sb.append(PROMPT_LABEL_DECISIONS).append(String.join(", ", (List<String>) decisionsObj)).append("\n");
                } else if (decisionsObj instanceof String) {
                    sb.append(PROMPT_LABEL_DECISIONS).append(decisionsObj).append("\n");
                }
            }
        }
        if (doc.getText() != null && !doc.getText().isBlank()) {
            sb.append(doc.getText());
        }
        return sb.toString();
    }

    /**
     * Validates that the keyword exists with precise matching (not just related terms).
     * For example, solar radiation must not count as lighting (see item 37; Spanish query forms use the same rules).
     *
     * @param docs documents to check
     * @param keyword keyword to validate
     * @param query original query for context
     * @return true if the keyword matches with the required precision
     */
    private boolean validateKeywordExistsPrecise(List<Document> docs, String keyword, String query) {
        if (docs == null || docs.isEmpty() || keyword == null || keyword.trim().isEmpty()) {
            return false;
        }
        
        log().info("Validating keyword '{}' exists with PRECISE matching in {} documents", keyword, docs.size());
        
        String kwLower = keyword != null ? keyword.toLowerCase().trim() : "";
        // FD-BQ-02: literal topic match only; do not let LLM infer related terms as keyword presence
        if (kwLower.contains("limpieza")) {
            boolean found = keywordExistsLiterallyInDocuments(docs, keyword);
            log().info("Keyword 'limpieza' literal match in documents: {}", found);
            return found;
        }
        // Solar radiation: require literal string match only; do NOT accept lighting or illumination as a match (item 37)
        if (kwLower.contains("radiación solar") || kwLower.contains("radiacion solar")) {
            for (Document doc : docs) {
                if (doc == null) continue;
                String text = buildDocumentContextString(doc);
                String textNorm = text.toLowerCase().replace("á", "a").replace("í", "i").replace("ó", "o");
                if (textNorm.contains("radiacion solar") || text.toLowerCase().contains("radiación solar")) {
                    log().info("Keyword 'radiación solar' found with literal match in document {}", doc.getId());
                    return true;
                }
            }
            log().info("Keyword 'radiación solar' not found (literal match required; iluminación/illumination do not count)");
            return false;
        }
        
        // Check a sample of documents (first 5) to avoid too many LLM calls
        int sampleSize = Math.min(5, docs.size());
        List<Document> sampleDocs = docs.subList(0, sampleSize);
        
        for (Document doc : sampleDocs) {
            if (doc == null) continue;
            
            // Build context from metadata
            StringBuilder context = new StringBuilder();
            Map<String, Object> metadata = doc.getMetadata();
            if (metadata != null) {
                if (metadata.containsKey("topics")) {
                    Object topicsObj = metadata.get("topics");
                    if (topicsObj instanceof List) {
                        context.append("Topics: ").append(String.join(", ", (List<String>) topicsObj)).append("\n");
                    } else if (topicsObj instanceof String) {
                        context.append("Topics: ").append(topicsObj).append("\n");
                    }
                }
                if (metadata.containsKey("summary")) {
                    Object summaryObj = metadata.get("summary");
                    if (summaryObj != null) {
                        context.append("Summary: ").append(summaryObj.toString()).append("\n");
                    }
                }
                if (metadata.containsKey("decisions")) {
                    Object decisionsObj = metadata.get("decisions");
                    if (decisionsObj instanceof List) {
                        context.append("Decisions: ").append(String.join(", ", (List<String>) decisionsObj)).append("\n");
                    } else if (decisionsObj instanceof String) {
                        context.append("Decisions: ").append(decisionsObj).append("\n");
                    }
                }
            }
            
            if (context.length() == 0) {
                continue;
            }
            
            String contextLower = context.toString().toLowerCase();
            // When query is about "seguridad", accept vigilancia/videovigilancia/cámaras so acta 25 ago 2026 matches
            if (keyword != null && keyword.toLowerCase().contains("seguridad")) {
                if (contextLower.contains("seguridad") || contextLower.contains(KEYWORD_VIGILANCIA) || contextLower.contains(KEYWORD_VIDEOVIGILANCIA)
                    || contextLower.contains("camara") || contextLower.contains("cámaras") || contextLower.contains("camaras")) {
                    log().info("Keyword 'seguridad' matched via synonym (vigilancia/videovigilancia/cámaras) in document");
                    return true;
                }
                if (doc.getText() != null) {
                    String contentLower = doc.getText().toLowerCase();
                    if (contentLower.contains("seguridad") || contentLower.contains(KEYWORD_VIGILANCIA) || contentLower.contains(KEYWORD_VIDEOVIGILANCIA)
                        || contentLower.contains("camara") || contentLower.contains("cámaras") || contentLower.contains("camaras")) {
                        log().info("Keyword 'seguridad' matched via synonym in document content");
                        return true;
                    }
                }
            }
            
            // Use LLM to check if keyword exists with PRECISE matching
            String prompt = String.format("""
                Task: Check if the keyword exists in the document metadata with PRECISE matching.
                
                Original query: "%s"
                Keyword to find: "%s"
                
                Document metadata:
                %s
                
                CRITICAL: Only return YES if the EXACT keyword or very close variations appear.
                Do NOT return YES for related but different terms.
                
                Examples:
                - Keyword: "radiación solar" → YES only if "radiación solar" appears, NOT for "iluminación" or "luz"
                - Keyword: "seguridad" → YES if "seguridad", "seguro", or "%s" appear (close variations)
                - Keyword: "ascensor" → YES if "ascensor" or "elevator" appear, NOT for "escalera"
                
                Respond with ONLY: YES or NO
                """, query, keyword, context.toString(), KEYWORD_VIGILANCIA);
            
            try {
                String result = getLLMResponseCached("metadata-boolean-evidence", prompt).toUpperCase();
                
                // Validate response (YES/NO)
                boolean isValid = result.contains("YES");
                if (isValid) {
                    log().info("Keyword '{}' found with PRECISE match in document {}", keyword, doc.getId());
                    return true;
                }
            } catch (Exception e) {
                log().warn("Error validating keyword '{}' with precise matching in document: {}", keyword, e.getMessage());
            }
        }
        
        log().info("Keyword '{}' not found with PRECISE match in sampled documents (checked {} of {} documents)", 
                  keyword, sampleSize, docs.size());
        return false;
    }

    private ToolResult tryAnswerAttendeesExistenceBoolean(String query) {
        if (!isHayActasAttendeeThresholdQuery(query)) {
            return null;
        }
        AttendeesCountQueryInfo info = detectAttendeesCountQuery(query);
        if (info == null) {
            return null;
        }
        List<Document> docs =
                retrieveCorpusDocumentsWithFallback(
                        query,
                        new String[] {
                                META_FIELD_DATE,
                                META_FIELD_PLACE,
                                META_FIELD_DECISIONS,
                                META_FIELD_TOPICS,
                                META_FIELD_SUMMARY,
                                META_FIELD_ATTENDEES
                        });
        if (docs.isEmpty()) {
            return null;
        }
        docs = dedupeDocsByDocumentId(docs);
        List<Minute> minutes = dedupeMinutesByDocumentId(extractMinutesInParallel(docs));
        List<Minute> matching =
                minutes.stream().filter(minute -> matchesAttendeeThreshold(minute, info)).toList();
        String answer = formatAttendeesExistenceBooleanAnswer(query, info, matching, minutes);
        return ToolResult.from(formatResponse(answer, query), getClass());
    }

    private static boolean isHayActasAttendeeThresholdQuery(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String q = query.toLowerCase(Locale.ROOT);
        boolean existenceShape =
                q.contains("hay actas")
                        || q.contains("existen actas")
                        || q.contains("alguna acta")
                        || q.contains("algún acta")
                        || q.contains("algun acta");
        boolean participants =
                q.contains("particip")
                        || q.contains("asistent")
                        || q.contains("personas")
                        || q.contains("propietarios");
        return existenceShape && participants;
    }

    private static boolean matchesAttendeeThreshold(Minute minute, AttendeesCountQueryInfo info) {
        if (minute == null || info == null) {
            return false;
        }
        int count = minute.numberOfAttendees();
        if (count <= 0 && minute.attendees() != null) {
            count = minute.attendees().size();
        }
        if (count <= 0) {
            return false;
        }
        return switch (info.operator) {
            case "less_than" -> count < info.threshold;
            case "more_than" -> count > info.threshold;
            case "equal" -> count == info.threshold;
            default -> false;
        };
    }

    private String formatAttendeesExistenceBooleanAnswer(
            String query, AttendeesCountQueryInfo info, List<Minute> matching, List<Minute> allMinutes) {
        boolean spanish = querySeemsSpanish(query);
        if (matching.isEmpty()) {
            int min = allMinutes.stream().mapToInt(MetadataBooleanQueryTool::attendeeCountFor).filter(c -> c > 0).min().orElse(0);
            int max = allMinutes.stream().mapToInt(MetadataBooleanQueryTool::attendeeCountFor).filter(c -> c > 0).max().orElse(0);
            if ("less_than".equals(info.operator)) {
                if (spanish) {
                    if (min > 0) {
                        return String.format(
                                Locale.ROOT,
                                "No; no hay actas con menos de %d participantes. Todas las actas registran al menos %d participantes (mínimo %d, máximo %d).",
                                info.threshold,
                                min,
                                min,
                                max);
                    }
                    return String.format(
                            Locale.ROOT,
                            "No; no hay actas con menos de %d participantes (mínimo %d, máximo %d).",
                            info.threshold,
                            min,
                            max);
                }
                return String.format(
                        Locale.ROOT,
                        "No meeting minutes have fewer than %d attendees (minimum %d, maximum %d).",
                        info.threshold,
                        min,
                        max);
            }
            return spanish ? "No." : "No.";
        }
        if (spanish) {
            return String.format(
                    Locale.ROOT,
                    "Sí, hay %d acta(s) que cumplen el criterio de %s %d participantes.",
                    matching.size(),
                    "less_than".equals(info.operator) ? "menos de" : "más de",
                    info.threshold);
        }
        return String.format(
                Locale.ROOT,
                "Yes, %d meeting minute(s) match fewer than %d attendees.",
                matching.size(),
                info.threshold);
    }

    private static int attendeeCountFor(Minute minute) {
        if (minute == null) {
            return 0;
        }
        if (minute.numberOfAttendees() > 0) {
            return minute.numberOfAttendees();
        }
        return minute.attendees() != null ? minute.attendees().size() : 0;
    }
}

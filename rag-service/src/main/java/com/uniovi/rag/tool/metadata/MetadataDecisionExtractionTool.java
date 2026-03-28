package com.uniovi.rag.tool.metadata;

import com.uniovi.rag.model.*;
import com.uniovi.rag.service.extraction.DocumentContentExtractor;
import com.uniovi.rag.service.retriever.ContextRetriever;
import com.uniovi.rag.tool.ToolExecutionContext;
import com.uniovi.rag.tool.ToolResult;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.uniovi.rag.observability.ContextPropagatingFutures.supplyAsync;
import java.util.stream.Collectors;

/**
 * Enhanced MetadataDecisionExtractionTool for extracting and analyzing meeting decisions with intelligent processing.
 */
public class MetadataDecisionExtractionTool extends AbstractMetadataTool {

    public MetadataDecisionExtractionTool(ChatClient chatClient, ContextRetriever retriever, DocumentContentExtractor extractor,
            MetadataLlmResponseCacheService llmResponseCache) {
        super(chatClient, retriever, extractor, llmResponseCache);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        if (query == null) {
            query = "";
        }
        try {
            return executeDecisionExtraction(query, ner);
        } catch (IllegalArgumentException e) {
            log().error("IllegalArgumentException in decision extraction (query: '{}'). Stack trace:", 
                    query.length() > 80 ? query.substring(0, 80) + "..." : query, e);
            return ToolResult.from(formatResponse(generateNotFoundMessage(query), query), getClass());
        } catch (RuntimeException e) {
            log().error("RuntimeException in decision extraction (query: '{}'). Stack trace:", 
                    query.length() > 80 ? query.substring(0, 80) + "..." : query, e);
            return ToolResult.from(formatResponse(generateNotFoundMessage(query), query), getClass());
        }
    }

    private ToolResult executeDecisionExtraction(String query, JSONObject ner) {
        log().info("Executing decision extraction query: {} with NER: {}", query, ner != null ? ner.toString() : "null");
        
        // Step 1: Retrieve and filter documents efficiently with fallback (using NER if available)
        List<Document> docs = retrieveDocumentsWithFallback(
            query,
            new String[] {"date", "place", "topics", "decisions", "summary"},
            ner
        );
        
        // Extract date early for error messages and validation (null-safe)
        List<String> dateCandidates = extractDateCandidates(query, ner != null ? ner : null);
        String date = (dateCandidates != null && !dateCandidates.isEmpty()) ? dateCandidates.get(0) : null;
        
        if (docs.isEmpty()) {
            log().info("No documents found for decision extraction query: {}", query);
            return ToolResult.from(formatResponse(generateSpecificErrorMessage(query, "decisions", date, 0, "no_documents"), query), getClass());
        }

        // Step 2: Extract minutes in parallel
        List<Minute> minutes = extractMinutesInParallel(docs);
        if (minutes.isEmpty()) {
            log().info("No valid minutes found for decision extraction query: {}", query);
            return ToolResult.from(formatResponse(generateSpecificErrorMessage(query, "decisions", date, docs.size(), "no_valid_minutes"), query), getClass());
        }

        // Step 3: Filter relevant minutes based on NER or query relevance
        List<Minute> relevantMinutes = filterRelevantMinutes(query, minutes, ner);
        if (relevantMinutes.isEmpty()) {
            log().info("No relevant minutes found for decision extraction query: {}", query);
            return ToolResult.from(formatResponse(generateSpecificErrorMessage(query, "decisions", date, minutes.size(), "no_relevant_minutes"), query), getClass());
        }

        // Step 4: Filter by date first (if query includes date) - early filtering reduces LLM calls
        List<Minute> dateFilteredMinutes = filterMinutesByDate(query, ner, relevantMinutes);
        if (dateFilteredMinutes.isEmpty() && dateCandidates != null && !dateCandidates.isEmpty()) {
            // User asked about a specific date but no minutes matched
            log().info("No minutes found for the specified date in query: {}", query);
            return ToolResult.from(formatResponse(generateSpecificErrorMessage(query, "decisions", date, relevantMinutes.size(), "date_not_found"), query), getClass());
        }
        // If no date in query, use all relevant minutes
        List<Minute> minutesToEvaluate = dateFilteredMinutes.isEmpty() ? relevantMinutes : dateFilteredMinutes;

        // Step 5: Evaluate each minute with LLM to validate it contains the requested decisions
        List<Minute> validatedMinutes = evaluateMinutesWithLLM(query, minutesToEvaluate);
        if (validatedMinutes.isEmpty()) {
            log().info("No minutes validated by LLM for decision extraction query: {}", query);
            return ToolResult.from(formatResponse(generateSpecificErrorMessage(query, "decisions", date, minutesToEvaluate.size(), "no_validated_minutes"), query), getClass());
        }

        // PHASE 6: Validate that minute dates match query date (if date was specified)
        if (date != null && !date.trim().isEmpty()) {
            List<Minute> dateValidatedMinutes = validatedMinutes.stream()
                    .filter(minute -> {
                        if (minute.date() == null) {
                            log().debug("Minute {} has no date, excluding when date '{}' was requested", minute.id(), date);
                            return false; // Skip minutes without date when date was requested
                        }
                        // Use flexible date matching (parseDateFlexible normalizes to lowercase internally)
                        LocalDate queryDate = parseDateFlexible(date);
                        LocalDate minuteDate = parseDateFlexible(minute.date());
                        
                        if (queryDate == null) {
                            log().warn("Could not parse query date '{}' for date validation. Keeping minute {} to avoid false negatives.", 
                                      date, minute.id());
                            return true; // If we can't parse query date, keep minute (conservative)
                        }
                        
                        if (minuteDate == null) {
                            log().warn("Could not parse minute date '{}' for minute {} (ID: {}). " +
                                      "This may indicate an unsupported date format. Excluding from results.", 
                                      minute.date(), minute.id(), minute.id());
                            return false; // Can't parse minute date, exclude
                        }
                        
                        // Exact match or same year/month
                        boolean matches = queryDate.equals(minuteDate) || 
                                        (queryDate.getYear() == minuteDate.getYear() && 
                                         queryDate.getMonth() == minuteDate.getMonth());
                        if (!matches) {
                            log().debug("Filtering out minute {} with date {} (parsed: {}) - requested: {} (parsed: {})", 
                                      minute.id(), minute.date(), minuteDate, date, queryDate);
                        } else {
                            log().debug("Minute {} date {} (parsed: {}) matches requested date {} (parsed: {})", 
                                      minute.id(), minute.date(), minuteDate, date, queryDate);
                        }
                        return matches;
                    })
                    .toList();
            
            if (dateValidatedMinutes.isEmpty()) {
                log().debug("No minutes with matching date found after validation. Query date: '{}' (parsed: {}). " +
                          "Validated {} minutes before date filtering. Date not in corpus is expected for some queries.", 
                          date, parseDateFlexible(date), validatedMinutes.size());
                return ToolResult.from(formatResponse(generateSpecificErrorMessage(query, "decisions", date, validatedMinutes.size(), "date_mismatch"), query), getClass());
            }
            log().info("Date validation passed: {} minutes match date '{}' out of {} validated minutes", 
                      dateValidatedMinutes.size(), date, validatedMinutes.size());
            validatedMinutes = dateValidatedMinutes;
        }

        // Step 6: Extract decisions in parallel (only from validated minutes)
        List<Decision> decisions = extractDecisionsInParallel(query, validatedMinutes);
        if (decisions.isEmpty()) {
            log().info("No relevant decisions found for query: {} (checked {} minutes)", query, validatedMinutes.size());
            return ToolResult.from(formatResponse(generateSpecificErrorMessage(query, "decisions", date, validatedMinutes.size(), "no_decisions_in_metadata"), query), getClass());
        }

        // Step 6.5: When query asks about a specific topic (e.g. "fuga de gas", "iluminación", "limpieza"), return "no mention" if no decision mentions it (§4). Use synonyms (items 14, 40).
        String topic = extractTopicFromQuery(query, ner);
        if (topic != null && !topic.isBlank() && isTopicSpecificQuery(query)) {
            boolean anyMentions = decisions.stream().anyMatch(d -> decisionMentionsTopic(d, topic));
            if (!anyMentions) {
                String noMentionMsg = generateTopicNotMentionedMessage(query, topic);
                return ToolResult.from(formatResponse(noMentionMsg, query), getClass());
            }
            // Filter to only decisions that mention the topic (or synonyms) so the answer focuses on it (item 14)
            decisions = decisions.stream().filter(d -> decisionMentionsTopic(d, topic)).toList();
        }

        // Step 7: Analyze and rank decisions
        List<Decision> rankedDecisions = analyzeAndRankDecisions(decisions);

        // Step 8: Cluster similar decisions
        List<DecisionCluster> clusters = clusterDecisions(rankedDecisions);

        // Step 9: Generate enhanced final answer (pass topic so LLM can link decisions to it — item 15)
        String answer = generateEnhancedDecisionAnswer(query, topic, rankedDecisions, clusters);
        log().info("Generated decision extraction answer for query: {} with {} decisions in {} clusters", 
                   query, decisions.size(), clusters.size());
        
        return ToolResult.from(formatResponse(answer, query), getClass());
    }

    /** True when the query asks what was said/commented about a specific topic (e.g. gas leak, lighting mentions). */
    private boolean isTopicSpecificQuery(String query) {
        if (query == null || query.isBlank()) return false;
        String q = query.toLowerCase();
        return q.contains("qué se comentó") || q.contains("qué se dijo") || q.contains("what was said")
                || q.contains("respecto a") || (q.contains("sobre") && (q.contains("coment") || q.contains("mencion")))
                || (q.contains("ocasiones") && (q.contains("mencion") || q.contains("mencionó") || q.contains("menciono")));
    }

    /** Message when the requested topic is not mentioned in any decision (§4 e.g. gas leak). */
    private String generateTopicNotMentionedMessage(String query, String topic) {
        if (query != null && query.toLowerCase().matches("(?s).*[áéíóúñ].*")) {
            return String.format("No se encuentra ninguna mención a \"%s\" en las actas disponibles.", topic != null ? topic : "ese tema");
        }
        return String.format("No mention of \"%s\" was found in the available meeting minutes.", topic != null ? topic : "that topic");
    }

    /**
     * Evaluates minutes with LLM to validate they contain the requested decisions.
     * Only minutes that pass validation are used for decision extraction.
     */
    private List<Minute> evaluateMinutesWithLLM(String query, List<Minute> minutes) {
        List<CompletableFuture<Minute>> futures = minutes.stream()
                .map(minute -> supplyAsync(() -> {
                    if (evaluateMinuteContainsRequestedInfo(query, minute)) {
                        return minute;
                    }
                    return null;
                }))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Extracts decisions in parallel
     */
    private List<Decision> extractDecisionsInParallel(String query, List<Minute> minutes) {
        List<CompletableFuture<List<Decision>>> futures = minutes.stream()
                .map(minute -> supplyAsync(() -> extractDecisionsFromMinute(query, minute)))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Extracts decisions from a single minute
     */
    private List<Decision> extractDecisionsFromMinute(String query, Minute minute) {
        if (minute.decisions() == null || minute.decisions().isEmpty()) {
            return Collections.emptyList();
        }

        List<Decision> decisions = new ArrayList<>();
        for (String decisionText : minute.decisions()) {
            // Metadata-first: keep all decisions, rely on later ranking instead of LLM filtering
            Decision decision = buildDecisionWithContext(minute, decisionText);
            if (decision != null) {
                decisions.add(decision);
            }
        }
        
        return decisions;
    }

    /**
     * Builds a decision with enhanced context
     */
    private Decision buildDecisionWithContext(Minute minute, String decisionText) {
        // Extract decision type via LLM (could be replaced by regex/keywords if needed)
        String decisionType = analyzeDecisionType(decisionText);

        // Extract key entities via LLM (fallback to simple parsing)
        List<String> keyEntities = extractKeyEntitiesFromDecision(decisionText);

        return new Decision(
            minute.id(),
            minute.date(),
            minute.place(),
            decisionText,
            decisionType,
            keyEntities,
            System.currentTimeMillis()
        );
    }

    /**
     * Analyzes decision type using rule-based classification (no LLM)
     */
    private String analyzeDecisionType(String decisionText) {
        if (decisionText == null || decisionText.isBlank()) {
            return "OTHER";
        }
        
        String text = decisionText.toLowerCase();
        
        // APPROVAL patterns
        if (text.contains("aprob") || text.contains("aprobar") || text.contains("acept") ||
            text.contains("aceptar") || text.contains("autoriz") || text.contains("autorizar") ||
            text.contains("ratific") || text.contains("ratificar")) {
            return "APPROVAL";
        }
        
        // REJECTION patterns
        if (text.contains("rechaz") || text.contains("deneg") || text.contains("denegar") ||
            text.contains("desestim") || text.contains("desestimar")) {
            return "REJECTION";
        }
        
        // ASSIGNMENT patterns
        if (text.contains("asign") || text.contains("asignar") || text.contains("encarg") ||
            text.contains("encargar") || text.contains("deleg") || text.contains("delegar") ||
            text.contains("responsabil") || text.contains("responsable")) {
            return "ASSIGNMENT";
        }
        
        // SCHEDULING patterns
        if (text.contains("fecha") || text.contains("plazo") || text.contains("program") ||
            text.contains("programar") || text.contains("agend") || text.contains("agendar") ||
            text.contains("convoc") || text.contains("convocar")) {
            return "SCHEDULING";
        }
        
        // POLICY patterns
        if (text.contains("norma") || text.contains("reglamento") || text.contains("política") ||
            text.contains("política") || text.contains("regulación") || text.contains("estatuto")) {
            return "POLICY";
        }
        
        // FINANCIAL patterns
        if (text.contains("presupuesto") || text.contains("presupuest") || text.contains("financi") ||
            text.contains("pago") || text.contains("gasto") || text.contains("ingreso") ||
            text.contains("€") || text.contains("euro") || text.contains("coste") ||
            text.contains("costo") || text.contains("tarifa") || text.contains("cuota")) {
            return "FINANCIAL";
        }
        
        // PERSONNEL patterns
        if (text.contains("personal") || text.contains("empleado") || text.contains("trabajador") ||
            text.contains("contrat") || text.contains("contratar") || text.contains("nombramiento") ||
            text.contains("design") || text.contains("designar") || text.contains("cargo")) {
            return "PERSONNEL";
        }
        
        return "OTHER";
    }

    /**
     * Extracts key entities from decision text using simple pattern matching
     */
    private List<String> extractKeyEntitiesFromDecision(String decisionText) {
        if (decisionText == null || decisionText.isBlank()) {
            return Collections.emptyList();
        }
        
        List<String> entities = new ArrayList<>();
        
        // Extract amounts (€, euros, numbers with currency)
        java.util.regex.Pattern amountPattern = java.util.regex.Pattern.compile("\\d+[.,]?\\d*\\s*€|\\d+[.,]?\\d*\\s*euro");
        java.util.regex.Matcher amountMatcher = amountPattern.matcher(decisionText);
        while (amountMatcher.find()) {
            entities.add(amountMatcher.group().trim());
        }
        
        // Extract dates (common Spanish date patterns)
        java.util.regex.Pattern datePattern = java.util.regex.Pattern.compile(
            "\\d{1,2}\\s+de\\s+[a-z]+\\s+de\\s+\\d{4}|\\d{1,2}/\\d{1,2}/\\d{4}|\\d{4}-\\d{2}-\\d{2}"
        );
        java.util.regex.Matcher dateMatcher = datePattern.matcher(decisionText);
        while (dateMatcher.find()) {
            entities.add(dateMatcher.group().trim());
        }
        
        // Extract capitalized words/phrases (likely names or organizations)
        java.util.regex.Pattern namePattern = java.util.regex.Pattern.compile(
            "\\b[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+(?:\\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+)+\\b"
        );
        java.util.regex.Matcher nameMatcher = namePattern.matcher(decisionText);
        while (nameMatcher.find()) {
            String name = nameMatcher.group().trim();
            // Filter out common words that are capitalized but not entities
            if (!name.matches("^(El|La|Los|Las|De|Del|En|Por|Para|Con|Sin|Sobre|Bajo|Entre|Durante|Según|Mediante)$")) {
                entities.add(name);
            }
        }
        
        return entities.stream()
                .distinct()
                .limit(10) // Limit to avoid too many entities
                .toList();
    }

    /**
     * Analyzes and ranks decisions by relevance and quality
     */
    private List<Decision> analyzeAndRankDecisions(List<Decision> decisions) {
        // Heuristic: longer decision text first
        return decisions.stream()
                .sorted((a, b) -> Integer.compare(
                        b.getDecisionText() != null ? b.getDecisionText().length() : 0,
                        a.getDecisionText() != null ? a.getDecisionText().length() : 0))
                .toList();
    }

    /**
     * Clusters similar decisions to avoid redundancy
     */
    private List<DecisionCluster> clusterDecisions(List<Decision> decisions) {
        List<DecisionCluster> clusters = new ArrayList<>();
        
        for (Decision decision : decisions) {
            boolean addedToCluster = false;
            
            // Try to add to existing cluster
            for (DecisionCluster cluster : clusters) {
                if (isSimilarToCluster(decision, cluster)) {
                    cluster.addDecision(decision);
                    addedToCluster = true;
                    break;
                }
            }
            
            // Create new cluster if not similar to any existing one
            if (!addedToCluster) {
                clusters.add(new DecisionCluster(decision));
            }
        }
        
        return clusters;
    }

    /**
     * Checks if a decision is similar to a cluster
     */
    private boolean isSimilarToCluster(Decision decision, DecisionCluster cluster) {
        // Check if decision types match
        if (!decision.getDecisionType().equals(cluster.getRepresentativeDecision().getDecisionType())) {
            return false;
        }
        
        // Check content similarity
        String decisionContent = decision.getDecisionText().toLowerCase();
        String clusterContent = cluster.getRepresentativeContent().toLowerCase();
        
        Set<String> decisionWords = new HashSet<>(Arrays.asList(decisionContent.split("\\s+")));
        Set<String> clusterWords = new HashSet<>(Arrays.asList(clusterContent.split("\\s+")));
        
        long commonWords = decisionWords.stream()
                .filter(clusterWords::contains)
                .count();
        
        double similarity = (double) commonWords / Math.max(decisionWords.size(), clusterWords.size());
        
        return similarity > 0.4; // Threshold for decision similarity
    }

    /**
     * Generates enhanced decision answer with clustering and analysis.
     * Uses English for internal processing, but response matches query language.
     * @param topic extracted topic from query (e.g. lighting); used to instruct LLM to link decisions to topic (item 15).
     */
    private String generateEnhancedDecisionAnswer(String query, String topic, List<Decision> decisions, List<DecisionCluster> clusters) {
        if (query == null || query.trim().isEmpty() || decisions == null || decisions.isEmpty()) {
            return generateNoDataMessage(query);
        }
        
        String decisionSummary = formatDecisionSummary(decisions, clusters);
        
        boolean asksOccasionsForTopic = query != null && (query.toLowerCase().contains("ocasiones") && query.toLowerCase().contains("mencion")
                || query.toLowerCase().contains("occasions") && query.toLowerCase().contains("mention"));
        String topicInstruction = asksOccasionsForTopic
                ? " When the user asks about occasions when a topic was mentioned, list each meeting/act where it was mentioned and explicitly link each decision to that topic (e.g. 'En relación con la iluminación: en la reunión X se…; en la reunión Y se…'). Do NOT say that the topic was not mentioned if you are listing decisions that do mention it."
                : "";
        if (topic != null && !topic.isBlank()) {
            topicInstruction += String.format(" The user asked about the topic \"%s\". In your answer, link each decision to this topic (e.g. 'En relación con la iluminación: …', 'mejoras junto a seguridad', 'reforzar su uso en zonas comunes').", topic);
        }

        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            Found %d relevant decisions:
            
            %s
            
            Write a clear, direct answer in the same language as the query.
            Provide only the information requested by the user.
            DO NOT mention any technical details like "clusters", "análisis", "analysis", "grouped into", or internal processing.
            DO NOT include phrases like "Basándonos en el análisis" or "Según los datos proporcionados".
            Focus on answering the question naturally and concisely, as if you were a helpful assistant.%s
            """, query, decisions.size(),
            decisionSummary != null ? decisionSummary : "No decisions found.",
            topicInstruction);
        
        try {
            String response = getLLMResponseCached(prompt);
            
            if (response == null || response.trim().isEmpty()) {
                log().warn("Empty response from LLM in generateEnhancedDecisionAnswer, using fallback");
                return generateFallbackDecisionAnswer(query, decisions);
            }
            
            return response;
        } catch (Exception e) {
            log().error("Error generating enhanced decision answer, using fallback", e);
            return generateFallbackDecisionAnswer(query, decisions);
        }
    }
    
    /**
     * Generates a fallback decision answer when LLM fails.
     * Uses LLM to generate message in correct language.
     */
    private String generateFallbackDecisionAnswer(String query, List<Decision> decisions) {
        String decisionsText = decisions.stream()
                .limit(5)
                .map(d -> String.format("- %s", d.getDecisionText()))
                .collect(Collectors.joining("\n"));
        
        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            Found %d relevant decisions:
            %s
            
            Respond with a short message in the EXACT SAME LANGUAGE as the question,
            listing the found decisions.
            Be concise and direct.
            Do not repeat the question.
            """, query != null ? query : "", decisions.size(), decisionsText);
        
        try {
            String response = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            if (response != null && !response.trim().isEmpty()) {
                return response.trim();
            }
        } catch (Exception e) {
            log().warn("Error generating fallback decision answer with LLM", e);
        }
        
        // Ultimate fallback
        return String.format("Found %d relevant decisions:%n%s",
                          decisions.size(), decisionsText);
    }

    /**
     * Formats decision summary for LLM prompt (without technical details)
     */
    private String formatDecisionSummary(List<Decision> decisions, List<DecisionCluster> clusters) {
        StringBuilder summary = new StringBuilder();
        
        // Format decisions naturally without mentioning clusters
        for (int i = 0; i < clusters.size(); i++) {
            DecisionCluster cluster = clusters.get(i);
            Decision representative = cluster.getRepresentativeDecision();
            
            if (representative.getDate() != null) {
                summary.append(String.format("Reunión del %s:\n", representative.getDate()));
            }
            summary.append(representative.getDecisionText() != null ? representative.getDecisionText() : "");
            summary.append("\n\n");
        }
        
        return summary.toString();
    }

    /** Terms (topic + synonyms) for matching decisions to a topic (items 14, 40). */
    private List<String> getTopicTermsForMatching(String topic) {
        if (topic == null || topic.isBlank()) return Collections.emptyList();
        String t = topic.toLowerCase().trim();
        List<String> terms = new ArrayList<>();
        terms.add(t);
        // Lighting (items 14, 15): include zones/security context so phrases about improvements near security, common areas, reinforcement match
        if (t.contains("iluminacion") || t.contains("iluminación")) {
            terms.add("iluminacion"); terms.add("iluminación"); terms.add("alumbrado"); terms.add("luz");
            terms.add("zonas comunes"); terms.add("iluminación de zonas"); terms.add("iluminacion de zonas");
            terms.add("mejoras junto a seguridad"); terms.add("reforzar");
        }
        // Limpieza / zonas comunes (item 40)
        if (t.contains("limpieza") || t.contains("zonas comunes")) {
            terms.add("limpieza"); terms.add("zonas comunes"); terms.add("servicio de limpieza");
            terms.add("cleaning"); terms.add("common areas"); terms.add("cleanliness");
        }
        return terms;
    }

    /** True if the decision text mentions the topic or any of its synonyms (items 14, 40). */
    private boolean decisionMentionsTopic(Decision d, String topic) {
        if (d == null || d.getDecisionText() == null || topic == null || topic.isBlank()) return false;
        String text = d.getDecisionText().toLowerCase();
        for (String term : getTopicTermsForMatching(topic)) {
            if (term != null && !term.isEmpty() && text.contains(term)) return true;
        }
        return false;
    }

}

package com.uniovi.rag.services.tools.metadata;

import com.uniovi.rag.model.*;
import com.uniovi.rag.services.retriever.ContextRetriever;
import com.uniovi.rag.services.tools.ToolExecutionContext;
import com.uniovi.rag.services.tools.ToolResult;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Enhanced MetadataDecisionExtractionTool for extracting and analyzing meeting decisions with intelligent processing.
 */
public class MetadataDecisionExtractionTool extends AbstractMetadataTool {

    public MetadataDecisionExtractionTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        
        log().info("Executing decision extraction query: {} with NER: {}", query, ner != null ? ner.toString() : "null");
        
        // Step 1: Retrieve and filter documents efficiently with fallback (using NER if available)
        List<Document> docs = retrieveDocumentsWithFallback(
            query,
            new String[] {"date", "place", "topics", "decisions", "summary"},
            ner
        );
        
        // Extract date early for error messages and validation
        List<String> dateCandidates = extractDateCandidates(query, ner);
        String date = dateCandidates.isEmpty() ? null : dateCandidates.get(0);
        
        if (docs.isEmpty()) {
            log().info("No documents found for decision extraction query: {}", query);
            return ToolResult.from(generateSpecificErrorMessage(query, "decisions", date, 0, "no_documents"), getClass());
        }

        // Step 2: Extract minutes in parallel
        List<Minute> minutes = extractMinutesInParallel(docs);
        if (minutes.isEmpty()) {
            log().info("No valid minutes found for decision extraction query: {}", query);
            return ToolResult.from(generateSpecificErrorMessage(query, "decisions", date, docs.size(), "no_valid_minutes"), getClass());
        }

        // Step 3: Filter relevant minutes based on NER or query relevance
        List<Minute> relevantMinutes = filterRelevantMinutes(query, minutes, ner);
        if (relevantMinutes.isEmpty()) {
            log().info("No relevant minutes found for decision extraction query: {}", query);
            return ToolResult.from(generateSpecificErrorMessage(query, "decisions", date, minutes.size(), "no_relevant_minutes"), getClass());
        }

        // Step 4: Filter by date first (if query includes date) - early filtering reduces LLM calls
        List<Minute> dateFilteredMinutes = filterMinutesByDate(query, ner, relevantMinutes);
        if (dateFilteredMinutes.isEmpty() && !dateCandidates.isEmpty()) {
            // User asked about a specific date but no minutes matched
            log().info("No minutes found for the specified date in query: {}", query);
            return ToolResult.from(generateSpecificErrorMessage(query, "decisions", date, relevantMinutes.size(), "date_not_found"), getClass());
        }
        // If no date in query, use all relevant minutes
        List<Minute> minutesToEvaluate = dateFilteredMinutes.isEmpty() ? relevantMinutes : dateFilteredMinutes;

        // Step 5: Evaluate each minute with LLM to validate it contains the requested decisions
        List<Minute> validatedMinutes = evaluateMinutesWithLLM(query, minutesToEvaluate);
        if (validatedMinutes.isEmpty()) {
            log().info("No minutes validated by LLM for decision extraction query: {}", query);
            return ToolResult.from(generateSpecificErrorMessage(query, "decisions", date, minutesToEvaluate.size(), "no_validated_minutes"), getClass());
        }

        // PHASE 6: Validate that minute dates match query date (if date was specified)
        if (date != null && !date.trim().isEmpty()) {
            List<Minute> dateValidatedMinutes = validatedMinutes.stream()
                    .filter(minute -> {
                        if (minute.date() == null) {
                            return false; // Skip minutes without date when date was requested
                        }
                        // Use flexible date matching
                        LocalDate queryDate = parseDateFlexible(date);
                        LocalDate minuteDate = parseDateFlexible(minute.date());
                        if (queryDate != null && minuteDate != null) {
                            // Exact match or same year/month
                            boolean matches = queryDate.equals(minuteDate) || 
                                            (queryDate.getYear() == minuteDate.getYear() && 
                                             queryDate.getMonth() == minuteDate.getMonth());
                            if (!matches) {
                                log().debug("Filtering out minute with date {} (requested: {})", minute.date(), date);
                            }
                            return matches;
                        }
                        // If parsing fails, keep the minute (conservative approach)
                        return true;
                    })
                    .collect(Collectors.toList());
            
            if (dateValidatedMinutes.isEmpty()) {
                log().warn("No minutes with matching date found after validation (query date: {})", date);
                return ToolResult.from(generateSpecificErrorMessage(query, "decisions", date, validatedMinutes.size(), "date_mismatch"), getClass());
            }
            validatedMinutes = dateValidatedMinutes;
        }

        // Step 6: Extract decisions in parallel (only from validated minutes)
        List<Decision> decisions = extractDecisionsInParallel(query, validatedMinutes);
        if (decisions.isEmpty()) {
            log().info("No relevant decisions found for query: {} (checked {} minutes)", query, validatedMinutes.size());
            return ToolResult.from(generateSpecificErrorMessage(query, "decisions", date, validatedMinutes.size(), "no_decisions_in_metadata"), getClass());
        }

        // Step 7: Analyze and rank decisions
        List<Decision> rankedDecisions = analyzeAndRankDecisions(decisions);

        // Step 8: Cluster similar decisions
        List<DecisionCluster> clusters = clusterDecisions(rankedDecisions);

        // Step 9: Generate enhanced final answer
        String answer = generateEnhancedDecisionAnswer(query, rankedDecisions, clusters);
        log().info("Generated decision extraction answer for query: {} with {} decisions in {} clusters", 
                   query, decisions.size(), clusters.size());
        
        return ToolResult.from(answer, getClass());
    }

    /**
     * Evaluates minutes with LLM to validate they contain the requested decisions.
     * Only minutes that pass validation are used for decision extraction.
     */
    private List<Minute> evaluateMinutesWithLLM(String query, List<Minute> minutes) {
        List<CompletableFuture<Minute>> futures = minutes.stream()
                .map(minute -> CompletableFuture.supplyAsync(() -> {
                    if (evaluateMinuteContainsRequestedInfo(query, minute)) {
                        return minute;
                    }
                    return null;
                }))
                .collect(Collectors.toList());

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Extracts decisions in parallel
     */
    private List<Decision> extractDecisionsInParallel(String query, List<Minute> minutes) {
        List<CompletableFuture<List<Decision>>> futures = minutes.stream()
                .map(minute -> CompletableFuture.supplyAsync(() -> extractDecisionsFromMinute(query, minute)))
                .collect(Collectors.toList());

        return futures.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
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
                .collect(Collectors.toList());
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
                .collect(Collectors.toList());
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
        
        Set<String> decisionWords = Set.of(decisionContent.split("\\s+"));
        Set<String> clusterWords = Set.of(clusterContent.split("\\s+"));
        
        long commonWords = decisionWords.stream()
                .filter(clusterWords::contains)
                .count();
        
        double similarity = (double) commonWords / Math.max(decisionWords.size(), clusterWords.size());
        
        return similarity > 0.4; // Threshold for decision similarity
    }

    /**
     * Generates enhanced decision answer with clustering and analysis.
     * Uses English for internal processing, but response matches query language.
     */
    private String generateEnhancedDecisionAnswer(String query, List<Decision> decisions, List<DecisionCluster> clusters) {
        if (query == null || query.trim().isEmpty() || decisions == null || decisions.isEmpty()) {
            return generateNoDataMessage(query);
        }
        
        String decisionSummary = formatDecisionSummary(decisions, clusters);
        
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            Found %d relevant decisions:
            
            %s
            
            Write a clear, direct answer in the same language as the query.
            Provide only the information requested by the user.
            DO NOT mention any technical details like "clusters", "análisis", "analysis", "grouped into", or internal processing.
            DO NOT include phrases like "Basándonos en el análisis" or "Según los datos proporcionados".
            Focus on answering the question naturally and concisely, as if you were a helpful assistant.
            """, query, decisions.size(), 
            decisionSummary != null ? decisionSummary : "No decisions found.");
        
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
     * Detects language from query and responds accordingly.
     */
    private String generateFallbackDecisionAnswer(String query, List<Decision> decisions) {
        String queryLower = query.toLowerCase();
        boolean isSpanish = queryLower.matches(".*[áéíóúñ¿¡].*");
        
        if (isSpanish) {
            return String.format("Se encontraron %d decisiones relevantes:\n%s",
                              decisions.size(),
                              decisions.stream()
                                      .limit(5)
                                      .map(d -> String.format("- %s", d.getDecisionText()))
                                      .collect(Collectors.joining("\n")));
        } else {
            return String.format("Found %d relevant decisions:\n%s",
                              decisions.size(),
                              decisions.stream()
                                      .limit(5)
                                      .map(d -> String.format("- %s", d.getDecisionText()))
                                      .collect(Collectors.joining("\n")));
        }
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

}

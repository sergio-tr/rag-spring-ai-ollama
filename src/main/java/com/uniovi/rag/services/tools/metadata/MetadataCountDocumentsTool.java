package com.uniovi.rag.services.tools.metadata;

import com.uniovi.rag.model.Minute;
import com.uniovi.rag.services.retriever.ContextRetriever;
import com.uniovi.rag.services.tools.ToolExecutionContext;
import com.uniovi.rag.services.tools.ToolResult;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.cache.annotation.Cacheable;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Enhanced MetadataCountDocumentsTool for counting meeting minutes with intelligent analysis.
 * 
 * Features:
 * - Intelligent counting with context analysis
 * - Parallel processing for better performance
 * - Cached evaluations for efficiency
 * - Temporal analysis and trend detection
 * - Statistical analysis of document distribution
 * - Advanced NER-based filtering
 */
public class MetadataCountDocumentsTool extends AbstractMetadataTool {

    public MetadataCountDocumentsTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        
        log().debug("Executing count documents query: {} with NER: {}", query, ner != null ? ner.toString() : "null");
        
        // Step 1: Retrieve and filter documents efficiently
        List<Document> docs = retrieveDocumentsWithMetadataFilter(
            query,
            new String[] {"date", "place", "topics", "decisions", "summary"}
        );
        if (docs.isEmpty()) {
            log().debug("No documents found for count query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 2: Extract minutes in parallel
        List<Minute> minutes = extractMinutesInParallel(docs);
        if (minutes.isEmpty()) {
            log().debug("No valid minutes found for count query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 3: Filter relevant minutes based on NER or query relevance
        List<Minute> relevantMinutes = filterRelevantMinutes(query, minutes, ner);
        if (relevantMinutes.isEmpty()) {
            log().debug("No relevant minutes found for count query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 4: Perform comprehensive counting analysis
        CountingAnalysis analysis = performCountingAnalysis(query, relevantMinutes);

        // Step 5: Generate enhanced count answer
        String answer = generateEnhancedCountAnswer(query, analysis);
        log().debug("Generated count answer for query: {} with {} documents", query, analysis.totalCount);
        
        return ToolResult.from(answer, getClass());
    }

    /**
     * Cached extraction of minute objects
     */
    @Cacheable(value = "minuteObjects", key = "#doc.id")
    public Minute getMinuteFromMetadataCached(Document doc) {
        return getMinuteFromMetadata(doc);
    }

    /**
     * Cached NER matching evaluation
     */
    @Cacheable(value = "nerMatching", key = "#minute.hashCode() + '_' + #ner.hashCode()")
    public boolean matchesMinuteWithNERCached(Minute minute, JSONObject ner) {
        return matchesMinuteWithNER(minute, ner);
    }

    /**
     * Performs comprehensive counting analysis
     */
    private CountingAnalysis performCountingAnalysis(String query, List<Minute> minutes) {
        int totalCount = minutes.size();
        
        // Extract and analyze dates
        List<String> dates = extractAndAnalyzeDates(minutes);
        
        // Extract and analyze places
        List<String> places = extractAndAnalyzePlaces(minutes);
        
        // Extract and analyze topics
        List<String> topics = extractAndAnalyzeTopics(minutes);
        
        // Perform temporal analysis
        TemporalAnalysis temporalAnalysis = performTemporalAnalysis(dates);
        
        // Perform distribution analysis
        DistributionAnalysis distributionAnalysis = performDistributionAnalysis(minutes);
        
        return new CountingAnalysis(
            totalCount,
            dates,
            places,
            topics,
            temporalAnalysis,
            distributionAnalysis
        );
    }

    /**
     * Extracts and analyzes dates from minutes
     */
    private List<String> extractAndAnalyzeDates(List<Minute> minutes) {
        return minutes.stream()
                .map(Minute::date)
                .filter(Objects::nonNull)
                .filter(date -> !date.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Extracts and analyzes places from minutes
     */
    private List<String> extractAndAnalyzePlaces(List<Minute> minutes) {
        return minutes.stream()
                .map(Minute::place)
                .filter(Objects::nonNull)
                .filter(place -> !place.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Extracts and analyzes topics from minutes
     */
    private List<String> extractAndAnalyzeTopics(List<Minute> minutes) {
        return minutes.stream()
                .map(Minute::topics)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Performs temporal analysis on dates
     */
    private TemporalAnalysis performTemporalAnalysis(List<String> dates) {
        if (dates.isEmpty()) {
            return new TemporalAnalysis(null, null, null, null, null);
        }

        // Parse dates and find temporal patterns
        List<LocalDate> parsedDates = parseDates(dates);
        
        if (parsedDates.isEmpty()) {
            return new TemporalAnalysis(null, null, null, null, null);
        }

        LocalDate earliestDate = Collections.min(parsedDates);
        LocalDate latestDate = Collections.max(parsedDates);
        long daysSpan = java.time.temporal.ChronoUnit.DAYS.between(earliestDate, latestDate);
        
        // Analyze monthly distribution
        Map<String, Integer> monthlyDistribution = analyzeMonthlyDistribution(parsedDates);
        
        // Analyze yearly distribution
        Map<Integer, Integer> yearlyDistribution = analyzeYearlyDistribution(parsedDates);
        
        return new TemporalAnalysis(earliestDate, latestDate, daysSpan, monthlyDistribution, yearlyDistribution);
    }

    /**
     * Parses date strings to LocalDate objects
     */
    private List<LocalDate> parseDates(List<String> dates) {
        List<LocalDate> parsedDates = new ArrayList<>();
        DateTimeFormatter[] formatters = {
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es")),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy")
        };
        
        for (String dateStr : dates) {
            for (DateTimeFormatter formatter : formatters) {
                try {
                    parsedDates.add(LocalDate.parse(dateStr, formatter));
                    break;
                } catch (DateTimeParseException e) {
                    // Try next formatter
                }
            }
        }
        
        return parsedDates;
    }

    /**
     * Analyzes monthly distribution of dates
     */
    private Map<String, Integer> analyzeMonthlyDistribution(List<LocalDate> dates) {
        return dates.stream()
                .collect(Collectors.groupingBy(
                    date -> date.getMonth().name() + " " + date.getYear(),
                    Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)
                ));
    }

    /**
     * Analyzes yearly distribution of dates
     */
    private Map<Integer, Integer> analyzeYearlyDistribution(List<LocalDate> dates) {
        return dates.stream()
                .collect(Collectors.groupingBy(
                    LocalDate::getYear,
                    Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)
                ));
    }

    /**
     * Performs distribution analysis on minutes
     */
    private DistributionAnalysis performDistributionAnalysis(List<Minute> minutes) {
        // Analyze place distribution
        Map<String, Integer> placeDistribution = minutes.stream()
                .map(Minute::place)
                .filter(Objects::nonNull)
                .filter(place -> !place.isBlank())
                .collect(Collectors.groupingBy(
                    place -> place,
                    Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)
                ));
        
        // Analyze topic distribution
        Map<String, Integer> topicDistribution = minutes.stream()
                .map(Minute::topics)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .collect(Collectors.groupingBy(
                    topic -> topic,
                    Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)
                ));
        
        // Analyze attendee distribution
        Map<Integer, Integer> attendeeDistribution = minutes.stream()
                .map(Minute::numberOfAttendees)
                .collect(Collectors.groupingBy(
                    attendees -> attendees,
                    Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)
                ));
        
        return new DistributionAnalysis(placeDistribution, topicDistribution, attendeeDistribution);
    }

    /**
     * Generates enhanced count answer with comprehensive analysis
     */
    private String generateEnhancedCountAnswer(String query, CountingAnalysis analysis) {
        String temporalInsights = formatTemporalInsights(analysis.temporalAnalysis);
        String distributionInsights = formatDistributionInsights(analysis.distributionAnalysis);
        
        String prompt = String.format("""
            Given the following counting query (in any language):
            "%s"
            
            Found %d relevant meeting minutes with the following analysis:
            
            Dates: %s
            Places: %s
            Topics: %s
            
            Temporal analysis:
            %s
            
            Distribution analysis:
            %s
            
            Write a clear, comprehensive answer in the same language as the query, 
            indicating the count and providing insights about the temporal and distribution patterns.
            Highlight the most significant findings and trends.
            """, 
            query, 
            analysis.totalCount,
            String.join(", ", analysis.dates),
            String.join(", ", analysis.places),
            String.join(", ", analysis.topics),
            temporalInsights,
            distributionInsights
        );
        
        return getLLMResponseCached(prompt);
    }

    /**
     * Formats temporal insights for LLM prompt
     */
    private String formatTemporalInsights(TemporalAnalysis temporalAnalysis) {
        if (temporalAnalysis.earliestDate == null) {
            return "No temporal analysis available.";
        }
        
        return String.format("""
            - Time span: %d days (from %s to %s)
            - Monthly distribution: %s
            - Yearly distribution: %s
            """,
            temporalAnalysis.daysSpan,
            temporalAnalysis.earliestDate.toString(),
            temporalAnalysis.latestDate.toString(),
            temporalAnalysis.monthlyDistribution.toString(),
            temporalAnalysis.yearlyDistribution.toString()
        );
    }

    /**
     * Formats distribution insights for LLM prompt
     */
    private String formatDistributionInsights(DistributionAnalysis distributionAnalysis) {
        return String.format("""
            - Place distribution: %s
            - Topic distribution: %s
            - Attendee distribution: %s
            """,
            distributionAnalysis.placeDistribution.toString(),
            distributionAnalysis.topicDistribution.toString(),
            distributionAnalysis.attendeeDistribution.toString()
        );
    }

    /**
     * Cached LLM response
     */
    @Cacheable(value = "llmResponses", key = "#prompt.hashCode()")
    public String getLLMResponseCached(String prompt) {
        return chatClient.prompt().user(prompt).call().content().strip();
    }

    /**
     * Represents comprehensive counting analysis results
     */
    private static class CountingAnalysis {
        final int totalCount;
        final List<String> dates;
        final List<String> places;
        final List<String> topics;
        final TemporalAnalysis temporalAnalysis;
        final DistributionAnalysis distributionAnalysis;

        CountingAnalysis(int totalCount, List<String> dates, List<String> places, List<String> topics,
                        TemporalAnalysis temporalAnalysis, DistributionAnalysis distributionAnalysis) {
            this.totalCount = totalCount;
            this.dates = dates;
            this.places = places;
            this.topics = topics;
            this.temporalAnalysis = temporalAnalysis;
            this.distributionAnalysis = distributionAnalysis;
        }
    }

    /**
     * Represents temporal analysis results
     */
    private static class TemporalAnalysis {
        final LocalDate earliestDate;
        final LocalDate latestDate;
        final Long daysSpan;
        final Map<String, Integer> monthlyDistribution;
        final Map<Integer, Integer> yearlyDistribution;

        TemporalAnalysis(LocalDate earliestDate, LocalDate latestDate, Long daysSpan,
                         Map<String, Integer> monthlyDistribution, Map<Integer, Integer> yearlyDistribution) {
            this.earliestDate = earliestDate;
            this.latestDate = latestDate;
            this.daysSpan = daysSpan;
            this.monthlyDistribution = monthlyDistribution;
            this.yearlyDistribution = yearlyDistribution;
        }
    }

    /**
     * Represents distribution analysis results
     */
    private static class DistributionAnalysis {
        final Map<String, Integer> placeDistribution;
        final Map<String, Integer> topicDistribution;
        final Map<Integer, Integer> attendeeDistribution;

        DistributionAnalysis(Map<String, Integer> placeDistribution, Map<String, Integer> topicDistribution,
                           Map<Integer, Integer> attendeeDistribution) {
            this.placeDistribution = placeDistribution;
            this.topicDistribution = topicDistribution;
            this.attendeeDistribution = attendeeDistribution;
        }
    }
}

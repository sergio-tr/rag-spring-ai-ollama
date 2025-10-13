package com.uniovi.rag.services.tools;

import com.uniovi.rag.services.retriever.ContextRetriever;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static com.uniovi.rag.utils.InfoExtractor.extractDate;
import static com.uniovi.rag.utils.InfoExtractor.extractTime;
import static com.uniovi.rag.utils.InfoExtractor.calculateDuration;

/**
 * Enhanced GetDurationTool for retrieving meeting durations with intelligent NER analysis.
 * 
 * Features:
 * - Intelligent NER-based filtering using EnhancedNERHandler
 * - Temporal context filtering
 * - Semantic duration relevance evaluation
 * - Enhanced duration extraction and comparison
 */
public class GetDurationTool extends AbstractTool {

    public GetDurationTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        
        log().debug("Executing get duration query: {} with NER: {}", query, ner != null ? ner.toString() : "null");
        
        List<Document> docs = retrieveDocuments(query);
        List<MeetingDuration> durations = new ArrayList<>();

        // Filter documents based on NER if available
        if (ner != null) {
            // Use EnhancedNERHandler for intelligent filtering
            List<Document> filteredDocs = nerHandler.filterDocumentsByTemporalContext(docs, ner);
            
            for (Document doc : filteredDocs) {
                if (nerHandler.matchesDocumentWithNER(doc, ner)) {
                    durations.add(extractMeetingDuration(doc));
                }
            }
        } else {
            // Fallback to LLM-based relevance
            for (Document doc : docs) {
                if (isRelevantByLLM(doc.getContent(), query)) {
                    durations.add(extractMeetingDuration(doc));
                }
            }
        }

        durations = durations.stream().filter(d -> d.durationMinutes > 0).collect(Collectors.toList());

        String answer;
        if (!durations.isEmpty()) {
            answer = generateFinalAnswer(query, durations);
        } else {
            answer = generateNotFoundMessage(query);
        }
        return ToolResult.from(answer, getClass());
    }

    /**
     * Determines if content is relevant to query using LLM
     */
    private boolean isRelevantByLLM(String content, String query) {
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            And the following meeting minutes content:
            "%s"
            
            Does this minutes document match all the conditions in the query? 
            Answer only YES or NO in the same language as the query.
            """, query, content.substring(0, Math.min(1000, content.length())));
        
        String result = chatClient
                .prompt()
                .user(prompt)
                .call()
                .content()
                .strip()
                .toLowerCase();
        
        // Check for positive responses in multiple languages
        return result.startsWith("yes") || result.startsWith("sí") || result.startsWith("si") || 
               result.startsWith("oui") || result.startsWith("ja") || result.startsWith("da");
    }

    /**
     * Extracts meeting duration from document
     */
    private MeetingDuration extractMeetingDuration(Document doc) {
        String content = doc.getContent();
        String date = extractDate(content);
        String startTime = extractTime(content, "start");
        String endTime = extractTime(content, "end");
        int duration = calculateDuration(content);
        return new MeetingDuration(date, startTime, endTime, duration);
    }

    /**
     * Generates final answer with found durations
     */
    private String generateFinalAnswer(String query, List<MeetingDuration> durations) {
        boolean isComparison = isComparisonQuery(query);
        if (isComparison) {
            MeetingDuration result = getComparisonResult(query, durations);
            if (result != null) {
                String prompt = String.format("""
                    Given the following user query (in any language):
                    "%s"
                    
                    The following meetings were found (date, start, end, duration in minutes):
                    %s
                    
                    Write a brief and clear answer, in the same language as the query, 
                    indicating which meeting had the longest/shortest duration and its details.
                    """, query, durations.stream().map(MeetingDuration::toString).collect(Collectors.joining("\n")));
                
                return chatClient
                        .prompt()
                        .user(prompt)
                        .call()
                        .content()
                        .strip();
            }
        }
        
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            The following meetings were found (date, start, end, duration in minutes):
            %s
            
            Write a brief and clear answer, in the same language as the query, 
            indicating the duration and details of each meeting found.
            """, query, durations.stream().map(MeetingDuration::toString).collect(Collectors.joining("\n")));
        
        return chatClient
                .prompt()
                .user(prompt)
                .call()
                .content()
                .strip();
    }

    /**
     * Determines if query is asking for comparison
     */
    private boolean isComparisonQuery(String query) {
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            Does the query ask for a comparison (e.g., longest, shortest, most, least, etc.)? 
            Answer only YES or NO in the same language as the query.
            """, query);
        
        String result = chatClient
                .prompt()
                .user(prompt)
                .call()
                .content()
                .strip()
                .toLowerCase();
        
        // Check for positive responses in multiple languages
        return result.startsWith("yes") || result.startsWith("sí") || result.startsWith("si") || 
               result.startsWith("oui") || result.startsWith("ja") || result.startsWith("da");
    }

    private MeetingDuration getComparisonResult(String query, List<MeetingDuration> durations) {
        String prompt = """
            Given the following user query (in any language):
            "%s"
            Does the query ask for the longest or the shortest duration? 
            Answer with "longest" or "shortest" (in English or the language of the query).
            """.formatted(query);
        String result = chatClient.prompt().user(prompt).call().content().strip().toLowerCase();
        if (result.contains("shortest") || result.contains("corta") || result.contains("menor")) {
            return durations.stream().min(Comparator.comparingInt(d -> d.durationMinutes)).orElse(null);
        } else {
            return durations.stream().max(Comparator.comparingInt(d -> d.durationMinutes)).orElse(null);
        }
    }

    private String generateNotFoundMessage(String query) {
        String prompt = """
            Given the following user query (in any language):
            "%s"
            Write a short message indicating that no information was found related to the query, in the same language as the query.
            """.formatted(query);
        return chatClient.prompt().user(prompt).call().content().strip();
    }

    private static class MeetingDuration {
        String date;
        String startTime;
        String endTime;
        int durationMinutes;

        public MeetingDuration(String date, String startTime, String endTime, int durationMinutes) {
            this.date = date;
            this.startTime = startTime;
            this.endTime = endTime;
            this.durationMinutes = durationMinutes;
        }

        @Override
        public String toString() {
            return date + ", " + (startTime != null ? startTime : "?") + " - " + (endTime != null ? endTime : "?") + ", " + durationMinutes + " minutos";
        }
    }
}

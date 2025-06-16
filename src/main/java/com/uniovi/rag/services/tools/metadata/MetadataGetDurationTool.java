package com.uniovi.rag.services.tools.metadata;

import com.uniovi.rag.model.Minute;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import java.util.ArrayList;
import java.util.List;
import com.uniovi.rag.services.tools.ToolExecutionContext;
import com.uniovi.rag.services.tools.ToolResult;
import com.uniovi.rag.services.retriever.ContextRetriever;
import java.util.stream.Collectors;

public class MetadataGetDurationTool extends AbstractMetadataTool {
    public MetadataGetDurationTool(ChatClient chatClient, ContextRetriever retriever) { super(chatClient, retriever); }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        List<Document> docs = retrieveAllDocuments(query);
        List<MinuteDuration> durations = new ArrayList<>();
        
        for (Document doc : docs) {
            Minute minute = getMinuteFromMetadata(doc);
            if (minute == null) continue;
            
            if (matchesBooleanCondition(doc, query, ner)) {
                MinuteDuration duration = extractMinuteDuration(minute);
                if (duration.durationMinutes > 0) {
                    durations.add(duration);
                }
            }
        }

        if (durations.isEmpty()) {
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        return ToolResult.from(generateFinalAnswer(query, durations), getClass());
    }

    private MinuteDuration extractMinuteDuration(Minute minute) {
        int duration = calculateDurationFromMinute(minute);
        return new MinuteDuration(minute.date(), minute.startTime(), minute.endTime(), duration);
    }

    private String generateFinalAnswer(String query, List<MinuteDuration> durations) {
        String prompt = """
                Given the following user query (in any language):
                "%s"
                
                And the following meeting durations:
                %s
                
                Write a clear answer in the same language as the query that:
                1. If the query asks for a comparison (longest/shortest), identify the relevant meeting
                2. Otherwise, list all meeting durations found
                Include dates and times in the response.
                """.formatted(
                    query,
                    durations.stream()
                        .map(MinuteDuration::toString)
                        .collect(Collectors.joining("\n"))
                );
        
        return chatClient.prompt().user(prompt).call().content().strip();
    }

    private String generateNotFoundMessage(String query) {
        String prompt = """
                Given the following user query (in any language):
                "%s"
                Write a short message indicating that no meeting durations were found, in the same language as the query.
                """.formatted(query);
        
        return chatClient.prompt().user(prompt).call().content().strip();
    }

    private static class MinuteDuration {
        String date;
        String startTime;
        String endTime;
        int durationMinutes;
        
        public MinuteDuration(String date, String startTime, String endTime, int durationMinutes) {
            this.date = date;
            this.startTime = startTime;
            this.endTime = endTime;
            this.durationMinutes = durationMinutes;
        }
        
        @Override
        public String toString() {
            return String.format("%s, %s - %s, %d minutos",
                date,
                startTime != null ? startTime : "?",
                endTime != null ? endTime : "?",
                durationMinutes
            );
        }
    }
}

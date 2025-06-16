package com.uniovi.rag.services.tools;

import com.uniovi.rag.services.retriever.ContextRetriever;
import org.json.JSONArray;
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

public class GetDurationTool extends AbstractTool {

    public GetDurationTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        List<Document> docs = retrieveDocuments(query);
        List<MeetingDuration> durations = new ArrayList<>();

        for (Document doc : docs) {
            if (ner != null) {
                if (matchesNER(doc, ner)) {
                    durations.add(extractMeetingDuration(doc));
                }
            } else {
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

    private boolean matchesNER(Document doc, JSONObject ner) {
        String[] fields = {"date", "place", "startTime", "endTime", "president", "secretary", "attendees", "numberOfAttendees", "agenda", "decisions", "mentionedEntities", "topics", "section", "summary"};
        String content = doc.getContent().toLowerCase();
        for (String field : fields) {
            if (ner.has(field)) {
                JSONArray arr = ner.optJSONArray(field);
                if (arr != null && arr.length() > 0) {
                    boolean anyMatch = false;
                    for (int i = 0; i < arr.length(); i++) {
                        String value = arr.getString(i).toLowerCase();
                        if (!value.isBlank() && content.contains(value)) {
                            anyMatch = true;
                            break;
                        }
                    }
                    if (!anyMatch) return false;
                }
            }
        }
        return true;
    }

    private boolean isRelevantByLLM(String content, String query) {
        String prompt = """
            Given the following user query (in any language):
            "%s"
            And the following minutes content:
            "%s"
            Does this minutes document match all the conditions in the query? 
            Answer only YES or NO (in the language of the query).
            """.formatted(query, content.substring(0, Math.min(1000, content.length())));
        String result = chatClient.prompt().user(prompt).call().content().strip().toLowerCase();
        return result.startsWith("yes") || result.startsWith("sí");
    }

    private MeetingDuration extractMeetingDuration(Document doc) {
        String content = doc.getContent();
        String date = extractDate(content);
        String startTime = extractTime(content, "start");
        String endTime = extractTime(content, "end");
        int duration = calculateDuration(content);
        return new MeetingDuration(date, startTime, endTime, duration);
    }

    private String generateFinalAnswer(String query, List<MeetingDuration> durations) {
        boolean isComparison = isComparisonQuery(query);
        if (isComparison) {
            MeetingDuration result = getComparisonResult(query, durations);
            if (result != null) {
                String prompt = """
                    Given the following user query (in any language):
                    "%s"
                    The following meetings were found (date, start, end, duration in minutes):
                    %s
                    Write a brief and clear answer, in the same language as the query, 
                    indicating which meeting had the longest/shortest duration and its details.
                    """.formatted(query, durations.stream().map(MeetingDuration::toString).collect(Collectors.joining("\n")));
                return chatClient.prompt().user(prompt).call().content().strip();
            }
        }
        String prompt = """
            Given the following user query (in any language):
            "%s"
            The following meetings were found (date, start, end, duration in minutes):
            %s
            Write a brief and clear answer, in the same language as the query, indicating the duration and details of each meeting found.
            """.formatted(query, durations.stream().map(MeetingDuration::toString).collect(Collectors.joining("\n")));
        return chatClient.prompt().user(prompt).call().content().strip();
    }

    private boolean isComparisonQuery(String query) {
        String prompt = """
            Given the following user query (in any language):
            "%s"
            Does the query ask for a comparison (e.g., longest, shortest, most, least, etc.)? 
            Answer only YES or NO (in the language of the query).
            """.formatted(query);
        String result = chatClient.prompt().user(prompt).call().content().strip().toLowerCase();
        return result.startsWith("yes") || result.startsWith("sí");
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

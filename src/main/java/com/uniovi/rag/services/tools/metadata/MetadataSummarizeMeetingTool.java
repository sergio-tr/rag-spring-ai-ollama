package com.uniovi.rag.services.tools.metadata;

import com.uniovi.rag.model.Minute;
import com.uniovi.rag.services.retriever.ContextRetriever;
import com.uniovi.rag.services.tools.ToolExecutionContext;
import com.uniovi.rag.services.tools.ToolResult;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.*;

public class MetadataSummarizeMeetingTool extends AbstractMetadataTool {


    public MetadataSummarizeMeetingTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        List<Document> docs = retrieveAllDocuments(query);
        List<String> summaries = new ArrayList<>();
        
        for (Document doc : docs) {
            Minute minute = getMinuteFromMetadata(doc);
            if (minute == null) continue;
            
            if (matchesBooleanCondition(doc, query, ner)) {
                if (isMeetingRelevant(minute, query) && minute.summary() != null && !minute.summary().isBlank()) {
                    summaries.add(minute.summary());
                }
                if (summaries.size() >= 10) break;
            }
        }
        
        if (summaries.isEmpty()) {
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }
        
        String summary = generateSummaryWithLLM(query, summaries);
        return ToolResult.from(summary, getClass());
    }

    private boolean isMeetingRelevant(Minute minute, String query) {
        String prompt = """
                Given the following user query (in any language):
                "%s"
                
                And this meeting metadata:
                Date: %s
                Place: %s
                Topics: %s
                Summary: %s
                
                Does the query ask about this specific meeting or its content?
                Consider semantic meaning, not just exact matches.
                Answer only with YES or NO.
                """.formatted(
                    query,
                    minute.date() != null ? minute.date() : "",
                    minute.place() != null ? minute.place() : "",
                    minute.topics() != null ? String.join(", ", minute.topics()) : "",
                    minute.summary() != null ? minute.summary() : ""
                );
                
        String result = chatClient.prompt().user(prompt).call().content().strip().toLowerCase();
        return result.contains("yes") || result.contains("sí");
    }

    private String generateSummaryWithLLM(String query, List<String> summaries) {
        String joined = String.join("\n\n", summaries);
        String prompt = """
                Given the following user query (in any language):
                "%s"
                
                The following are summaries of relevant meetings:
                %s
                
                Write a brief and clear summary in the same language as the query, 
                focusing on the key points mentioned.
                Avoid literal repetition and organize the information clearly.
                """.formatted(query, joined);
                
        return chatClient.prompt().user(prompt).call().content().strip();
    }

    private String generateNotFoundMessage(String query) {
        String prompt = """
            Given the following user query (in any language):
            "%s"
            Write a short message indicating that no information was found related to the query, 
            in the same language as the query.
        """
        .formatted(query);
        return chatClient.prompt().user(prompt).call().content().strip();
    }
}

package com.uniovi.rag.services.tools.metadata;

import com.uniovi.rag.model.Minute;
import com.uniovi.rag.services.retriever.ContextRetriever;
import com.uniovi.rag.services.tools.ToolExecutionContext;
import com.uniovi.rag.services.tools.ToolResult;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.*;

public class MetadataGetFieldTool extends AbstractMetadataTool {

    public MetadataGetFieldTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        List<Document> docs = retrieveAllDocuments(query);
        
        for (Document doc : docs) {
            Minute minute = getMinuteFromMetadata(doc);
            if (minute == null) continue;
            
            if (matchesBooleanCondition(doc, query, ner)) {
                String value = extractFieldByIntent(query, minute);
                if (value != null && !value.isBlank()) {
                    return ToolResult.from(value, getClass());
                }
            }
        }
        
        return ToolResult.from(generateNotFoundMessage(query), getClass());
    }

    private String extractFieldByIntent(String query, Minute minute) {
        String detectedField = classifyFieldIntentWithLLM(query);
        if (detectedField.equals("unknown")) return null;
        
        String value = extractFieldFromMinute(detectedField, minute);
        if (value == null || value.isBlank()) return null;
        
        String prompt = """
                Given the following user query (in any language):
                "%s"
                The value for field '%s' is: %s
                Write a clear, concise answer in the same language as the query, 
                presenting the value in a user-friendly way.
                """.formatted(query, detectedField, value);
                
        return chatClient.prompt().user(prompt).call().content().strip();
    }

    private String classifyFieldIntentWithLLM(String query) {
        String prompt = """
                Given the following user question (in any language):
                "%s"
                Determine which field the user wants to query. Choose one of the following:
                - date/fecha
                - place/lugar
                - startTime/hora_inicio
                - endTime/hora_fin
                - president/presidente
                - secretary/secretario
                
                Answer with the field name in English. If the intent is unclear, answer with "unknown".
                """.formatted(query);
                
        String result = chatClient.prompt().user(prompt).call().content().strip().toLowerCase();
        if (result.contains("unknown")) return "unknown";
        return result;
    }

    private String generateNotFoundMessage(String query) {
        String prompt = """
        Given the following user query (in any language):
        \"%s\"
        Write a short message indicating that no information was found related to the query, 
        in the same language as the query.
        """.formatted(query);
        return chatClient.prompt().user(prompt).call().content().strip();
    }
}

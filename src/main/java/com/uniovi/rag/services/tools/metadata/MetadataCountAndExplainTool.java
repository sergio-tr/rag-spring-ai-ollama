package com.uniovi.rag.services.tools.metadata;

import com.uniovi.rag.services.retriever.ContextRetriever;
import com.uniovi.rag.services.tools.ToolExecutionContext;
import com.uniovi.rag.services.tools.ToolResult;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.*;
import com.uniovi.rag.model.Minute;

public class MetadataCountAndExplainTool extends AbstractMetadataTool {

    public MetadataCountAndExplainTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        List<Document> docs = retrieveAllDocuments(query);
        
        List<String> explanations = new ArrayList<>();
        
        for (Document doc : docs) {
            Minute minute = getMinuteFromMetadata(doc);
            if (minute == null) continue;
            
            if (matchesBooleanCondition(doc, query, ner)) {
                String explanation = generateExplanation(query, minute);
                if (!explanation.isBlank()) {
                    explanations.add(explanation);
                }
            }
        }

        if (explanations.isEmpty()) {
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        String answer = generateFinalAnswer(query, explanations);
        return ToolResult.from(answer, getClass());
    }

    private String generateExplanation(String query, Minute minute) {
        String prompt = """
                Given the following user query (in any language):
                "%s"
                
                And the following meeting metadata:
                Date: %s
                Topics: %s
                Decisions: %s
                Summary: %s
                
                Write a brief explanation of what was discussed/decided regarding the query topic.
                Write in the same language as the query.
                """.formatted(
                    query,
                    minute.date() != null ? minute.date() : "",
                    minute.topics() != null ? String.join(", ", minute.topics()) : "",
                    minute.decisions() != null ? String.join(", ", minute.decisions()) : "",
                    minute.summary() != null ? minute.summary() : ""
                );
        
        return chatClient.prompt().user(prompt).call().content().strip();
    }

    private String generateFinalAnswer(String query, List<String> explanations) {
        String joined = String.join("\n\n", explanations);
        String prompt = """
        Given the following user query (in any language):
        \"%s\"
        There are %d relevant meeting minutes. Here are representative examples from their metadata:
        %s
        Write a clear, concise answer in the same language as the query, indicating the number of relevant minutes and summarizing the context found. Do not invent information.
        """.formatted(query, explanations.size(), joined);
        return chatClient.prompt().user(prompt).call().content().strip();
    }

    private String generateNotFoundMessage(String query) {
        String prompt = """
        Given the following user query (in any language):
        \"%s\"
        Write a short message indicating that no relevant meeting minutes were found, in the same language as the query.
        """.formatted(query);
        return chatClient.prompt().user(prompt).call().content().strip();
    }

}

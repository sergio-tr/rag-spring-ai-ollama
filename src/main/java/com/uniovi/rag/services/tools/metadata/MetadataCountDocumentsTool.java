package com.uniovi.rag.services.tools.metadata;

import com.uniovi.rag.services.retriever.ContextRetriever;
import com.uniovi.rag.services.tools.ToolExecutionContext;
import com.uniovi.rag.services.tools.ToolResult;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

import com.uniovi.rag.model.Minute;

public class MetadataCountDocumentsTool extends AbstractMetadataTool {

    public MetadataCountDocumentsTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        List<Document> docs = retrieveAllDocuments(query);
        
        // Contar documentos que cumplen la condición
        int count = 0;
        List<String> relevantDates = new ArrayList<>();
        
        for (Document doc : docs) {
            Minute minute = getMinuteFromMetadata(doc);
            if (minute == null) continue;
            
            if (matchesBooleanCondition(doc, query, ner)) {
                count++;
                if (minute.date() != null && !minute.date().isBlank()) {
                    relevantDates.add(minute.date());
                }
            }
        }

        if (count == 0) {
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        String answer = generateCountAnswerWithLLM(query, count, relevantDates);
        return ToolResult.from(answer, getClass());
    }

    private String generateCountAnswerWithLLM(String query, int count, List<String> dates) {
        String datesStr = dates.stream()
                .filter(f -> f != null && !f.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.joining(", "));
                
        String prompt = """
                Given the following user query (in any language):
                "%s"
                There are %d relevant meeting minutes. The dates of the relevant minutes are: %s
                Write a clear, concise answer in the same language as the query, using the number and the dates.
                """.formatted(query, count, datesStr.isBlank() ? "[no dates]" : datesStr);
                
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

package com.uniovi.rag.services.tools.metadata;

import com.uniovi.rag.services.retriever.ContextRetriever;
import com.uniovi.rag.services.tools.ToolExecutionContext;
import com.uniovi.rag.services.tools.ToolResult;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;

import com.uniovi.rag.model.Minute;

public class MetadataExtractEntitiesTool extends AbstractMetadataTool {

    public MetadataExtractEntitiesTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        List<Document> docs = retrieveAllDocuments(query);
        
        Set<String> entities = new HashSet<>();
        
        for (Document doc : docs) {
            Minute minute = getMinuteFromMetadata(doc);
            if (minute == null) continue;
            
            if (matchesBooleanCondition(doc, query, ner)) {
                entities.addAll(extractEntitiesFromMinute(minute));
            }
        }

        if (entities.isEmpty()) {
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        String answer = generateEntitiesAnswer(query, entities);
        return ToolResult.from(answer, getClass());
    }

    private Set<String> extractEntitiesFromMinute(Minute minute) {
        Set<String> entities = new HashSet<>();
        
        // Extraer personas
        if (minute.president() != null) entities.add("Presidente: " + minute.president());
        if (minute.secretary() != null) entities.add("Secretario: " + minute.secretary());
        if (minute.attendees() != null) {
            minute.attendees().forEach(a -> entities.add("Asistente: " + a));
        }
        
        // Extraer entidades de decisiones y temas
        if (minute.decisions() != null) {
            minute.decisions().forEach(d -> {
                String prompt = """
                        Extract all relevant entities (people, organizations, roles) from this text:
                        "%s"
                        List each entity on a new line.
                        """.formatted(d);
                String extracted = chatClient.prompt().user(prompt).call().content().strip();
                entities.addAll(Arrays.asList(extracted.split("\n")));
            });
        }
        
        return entities;
    }

    private String generateEntitiesAnswer(String query, Set<String> entities) {
        String joined = String.join("\n\n", entities);
        String prompt = """
        Given the following user query (in any language):
        \"%s\"
        The following are relevant entities from meeting minutes:
        %s
        Write a clear, concise answer in the same language as the query, summarizing the relevant entities and their context. Do not invent information.
        """.formatted(query, joined);
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

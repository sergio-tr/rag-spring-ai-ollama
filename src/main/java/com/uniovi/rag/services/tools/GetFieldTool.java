package com.uniovi.rag.services.tools;

import com.uniovi.rag.services.retriever.ContextRetriever;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.List;

import static com.uniovi.rag.utils.InfoExtractor.*;

public class GetFieldTool extends AbstractTool {

    public GetFieldTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        List<Document> docs = retrieveDocuments(query);

        for (Document doc : docs) {
            if (ner != null) {
                if (matchesNER(doc, ner)) {
                    String value = extractLiteralFieldByIntent(query, ner, doc.getContent());
                    if (value != null && !value.isBlank()) {
                        return ToolResult.from(value, getClass());
                    }
                }
            } else {
                if (isRelevantByLLM(doc.getContent(), query)) {
                    String value = extractLiteralFieldByIntent(query, null, doc.getContent());
                    if (value != null && !value.isBlank()) {
                        return ToolResult.from(value, getClass());
                    }
                }
            }
        }
        String notFound = generateNotFoundMessage(query);
        return ToolResult.from(notFound, getClass());
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
            Given the following user query (in any language):\n"%s"\nand the following minutes content:\n"%s"\n\nDoes this minutes document match all the conditions in the query? Answer only YES or NO (in the language of the query).
            """.formatted(query, content.substring(0, Math.min(1000, content.length())));
        String result = chatClient.prompt().user(prompt).call().content().strip().toLowerCase();
        return result.startsWith("yes") || result.startsWith("sí");
    }

    private String extractLiteralFieldByIntent(String query, JSONObject ner, String content) {
        String detectedField = classifyLiteralIntentWithLLM(query);
        return switch (detectedField) {
            case "date", "fecha" -> extractDate(content);
            case "startTime", "hora_inicio" -> extractTime(content, "start");
            case "endTime", "hora_fin" -> extractTime(content, "end");
            case "place", "lugar" -> extractLiteralField("place", content);
            case "president", "presidente" -> extractLiteralField("president", content);
            case "secretary", "secretario" -> extractLiteralField("secretary", content);
            case "attendees_list", "asistentes_lista" -> String.join(", ", extractAttendees(content));
            case "attendees_number", "asistentes_numero" -> String.valueOf(extractAttendeeCount(content));
            case "agenda", "orden_dia" -> extractAgenda(content);
            default -> null;
        };
    }

    private String classifyLiteralIntentWithLLM(String query) {
        String prompt = """
            Given the following user question (in any language):
            "%s"
            Determine which literal field the user wants to query. Choose one of the following (answer with the field name in the language of the question if possible):
            - date/fecha
            - place/lugar
            - startTime/hora_inicio
            - endTime/hora_fin
            - president/presidente
            - secretary/secretario
            - attendees_list/asistentes_lista
            - attendees_number/asistentes_numero
            - agenda/orden_dia
            If you cannot determine, answer exactly: unknown/desconocido
            """.formatted(query);
        String result = chatClient.prompt().user(prompt).call().content().strip().toLowerCase();
        if (result.contains("unknown") || result.contains("desconocido")) return "unknown";
        return result;
    }

    private String generateNotFoundMessage(String query) {
        String prompt = """
            Given the following user query (in any language):
            "%s"
            Write a short message indicating that no information was found related to the query, in the same language as the query.
            """.formatted(query);
        return chatClient.prompt().user(prompt).call().content().strip();
    }
}

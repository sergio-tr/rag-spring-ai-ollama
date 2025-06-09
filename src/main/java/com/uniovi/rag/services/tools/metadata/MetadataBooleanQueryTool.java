package com.uniovi.rag.services.tools.metadata;

import com.uniovi.rag.services.retriever.ContextRetriever;
import com.uniovi.rag.services.tools.ToolExecutionContext;
import com.uniovi.rag.services.tools.ToolResult;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.List;

public class MetadataBooleanQueryTool extends AbstractMetadataTool {

    public MetadataBooleanQueryTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    private static final String BOOL_PROMPT = """
            El usuario ha hecho la siguiente consulta:
            
            "%s"
            
            A continuación se listan fragmentos de actas relevantes extraídas del sistema:
            
            %s
            
            Redacta una respuesta clara y directa en español indicando si la información de las actas permite responder afirmativamente, negativamente o parcialmente a la consulta. Sé conciso pero informativo. Si la pregunta incluye una fecha o nombre concreto, haz mención explícita en la respuesta.
            """;


    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject nerEntities = ctx.nerEntities();

        String[] keywords = extractKeywordsFromQuery(query).split("\\s+");
        List<Document> docs = retrieveDocuments(query);

        // Filtramos los documentos que coinciden semánticamente
        List<String> relevantes = docs.stream()
                .filter(doc -> matchesBooleanCondition(doc, keywords, nerEntities))
                .map(doc -> (String) doc.getMetadata().get("date"))
                .distinct()
                .toList();

        if (relevantes.isEmpty()) {
            return ToolResult.from("No, no se menciona nada relacionado en las actas analizadas.", getClass());
        }

        // Creamos el contenido del prompt
        StringBuilder evidencias = new StringBuilder();
        for (String fecha : relevantes) {
            evidencias.append("- Acta del ").append(fecha).append("\n");
        }

        // Llamamos al LLM
        String respuesta = chatClient
                .prompt()
                .user(BOOL_PROMPT.formatted(query, evidencias.toString()))
                .call()
                .content()
                .strip();

        return ToolResult.from(respuesta, getClass());

    }

}

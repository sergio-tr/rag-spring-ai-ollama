package com.uniovi.rag.services.tools;

import com.uniovi.rag.services.retriever.ContextRetriever;
import com.uniovi.rag.utils.InfoExtractor;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.List;

import static com.uniovi.rag.utils.InfoExtractor.extractRelevantFragment;

public class BooleanQueryTool extends AbstractTool {

    public BooleanQueryTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities() != null ? ctx.nerEntities() : new JSONObject();
        JSONObject entities = ner.optJSONObject("entities") != null ? ner.optJSONObject("entities") : new JSONObject();
        JSONObject filtes = entities.optJSONObject("filtes") != null ? entities.optJSONObject("filtes") : new JSONObject();

        List<String> times = filtes.optJSONArray("date") != null
                ? filtes.optJSONArray("date").toList().stream().map(Object::toString).toList()
                : List.of();
        List<String> places = filtes.optJSONArray("place") != null
                ? filtes.optJSONArray("place").toList().stream().map(Object::toString).toList()
                : List.of();
        List<String> people = entities.optJSONArray("person") != null
                ? entities.optJSONArray("person").toList().stream().map(Object::toString).toList()
                : List.of();

        // Paso 1: inferir afirmación a verificar
        String affirm = inferClaimFromQuery(query);
        if (affirm.equalsIgnoreCase("desconocido") || affirm.isBlank()) {
            throw new RuntimeException("No se pudo inferir claramente la afirmación a verificar.");
        }

        // Paso 2: recuperar documentos
        List<Document> documentos = retrieveAllDocuments(query);
        for (Document doc : documentos) {
            String content = doc.getContent();
            String contentLower = content.toLowerCase();

            // Paso 3: aplicar filtros SOLO si existen
            boolean matchTime = times.isEmpty() || times.stream().anyMatch(f -> contentLower.contains(f.toLowerCase()));
            boolean matchPlace = places.isEmpty() || places.stream().anyMatch(l -> contentLower.contains(l.toLowerCase()));
            boolean matchPerson = people.isEmpty() || people.stream().anyMatch(p -> contentLower.contains(p.toLowerCase()));

            if (matchTime && matchPlace && matchPerson) {
                String fragment = extractRelevantFragment(content, affirm);
                if (fragment.toLowerCase().contains(affirm.toLowerCase())) {
                    String fecha = InfoExtractor.extractDate(content);
                    return ToolResult.from("Sí, se encontró evidencia de que \"" + affirm + "\" en la reunión del " + fecha + ":\n\n" + fragment, getClass());
                }
            }
        }

        return ToolResult.from("No se encontró evidencia de que \"" + affirm + "\" en las actas disponibles.", getClass());
    }

    private String inferClaimFromQuery(String query) {
        String prompt = """
                Esta es una pregunta del usuario sobre el contenido de una reunión:
                
                "%s"
                
                Redacta la afirmación principal que el usuario quiere verificar, como si ya hubiera ocurrido.
                Por ejemplo: "se aprobó el presupuesto", "se trató el tema de la calefacción", etc.
                Si no se puede deducir con claridad, responde solo con: desconocido.
                """.formatted(query);

        return chatClient
                .prompt()
                .user(prompt)
                .call()
                .content()
                .strip();
    }

    private boolean fragmentConfirmsClaim(String claim, String fragment) {
        String prompt = """
                Esta es una afirmación a verificar:
                "%s"
                
                Y este es un fragmento del acta:
                "%s"
                
                ¿Este fragmento confirma la afirmación? Responde solo con "sí" o "no".
                """.formatted(claim, fragment);

        String result = chatClient
                .prompt()
                .user(prompt)
                .call()
                .content()
                .strip()
                .toLowerCase();

        return result.contains("sí");
    }

}

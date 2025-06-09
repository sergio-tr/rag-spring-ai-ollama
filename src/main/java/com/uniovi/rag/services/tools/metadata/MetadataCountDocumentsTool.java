package com.uniovi.rag.services.tools.metadata;

import com.uniovi.rag.services.retriever.ContextRetriever;
import com.uniovi.rag.services.tools.ToolExecutionContext;
import com.uniovi.rag.services.tools.ToolResult;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.uniovi.rag.utils.InfoExtractor.containsAnyKeyword;
import static com.uniovi.rag.utils.InfoExtractor.containsRelevantPhrase;

public class MetadataCountDocumentsTool extends AbstractMetadataTool {

    private static final String COUNT_PROMPT = """
            El usuario desea saber cuántos documentos cumplen la siguiente condición:
            
            Pregunta: "%s"
            
            Ya se ha determinado el número de documentos relevantes: %d
            
            Redacta una única frase clara y directa en español, indicando ese número. No añadas más explicaciones.
            """;

    public MetadataCountDocumentsTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        List<Document> docs = retrieveAllDocuments(query);

        if (docs.isEmpty()) {
            throw new RuntimeException("No se encontraron actas que respondan a la consulta.");
        }

        Set<String> fechasRelevantes = (ner == null || ner.isEmpty())
                ? fechasByKeyword(query, docs)
                : fechasByNERAndMetadata(query, ner, docs);

        if (fechasRelevantes.isEmpty()) {
            return ToolResult.from("No se encontró ninguna acta que cumpla las condiciones especificadas.", getClass());
        }

        String listaFechas = String.join(", ", fechasRelevantes.stream().sorted().toList());
        String respuesta = "Se han encontrado " + fechasRelevantes.size() + " actas que cumplen las condiciones: " + listaFechas + ".";

        return ToolResult.from(respuesta, getClass());
    }


    private Set<String> fechasByKeyword(String query, List<Document> docs) {
        String keywordsRaw = extractKeywordsFromQuery(query);
        if (keywordsRaw == null || keywordsRaw.isBlank()) {
            throw new RuntimeException("No se encontraron palabras clave relevantes en la consulta.");
        }
        String[] keywords = keywordsRaw.toLowerCase().split("\\s+");

        Set<String> fechas = new HashSet<>();
        for (Document doc : docs) {
            if (containsAnyKeyword(doc.getContent(), keywords)) {
                Object date = doc.getMetadata().get("date");
                if (date != null) {
                    fechas.add(date.toString());
                }
            }
        }
        return fechas;
    }

    private Set<String> fechasByNERAndMetadata(String query, JSONObject ner, List<Document> docs) {
        JSONObject entidades = ner.optJSONObject("entities");
        JSONObject filtros = entidades != null ? entidades.optJSONObject("filters") : null;

        Set<String> personas = extractJSONArray(entidades, "person");
        Set<String> fechas = extractJSONArray(filtros, "date");
        Set<String> secciones = extractJSONArray(filtros, "section");
        Set<String> entidadesMencionadas = extractJSONArray(entidades, "entidad");

        Set<String> fechasRelevantes = new HashSet<>();

        for (Document doc : docs) {
            Map<String, Object> metadata = doc.getMetadata();
            String content = doc.getContent().toLowerCase();

            boolean matchPersona = personas.isEmpty() || personas.stream().anyMatch(p -> content.contains(p.toLowerCase()));
            boolean matchFecha = fechas.isEmpty() || fechas.stream().anyMatch(f -> metadata.getOrDefault("date", "").toString().toLowerCase().contains(f.toLowerCase()));
            boolean matchSeccion = secciones.isEmpty() || secciones.stream().anyMatch(s -> content.contains(s.toLowerCase()));
            boolean matchEntidadMetadata = entidadesMencionadas.isEmpty() || entidadesMencionadas.stream().anyMatch(e ->
                    metadata.getOrDefault("mentionedEntities", List.of()).toString().toLowerCase().contains(e.toLowerCase())
            );

            boolean matchSemantico = containsRelevantPhrase(content, query);

            if (matchPersona && matchFecha && matchSeccion && matchEntidadMetadata && matchSemantico) {
                Object date = metadata.get("date");
                if (date != null) {
                    fechasRelevantes.add(date.toString());
                }
            }
        }

        return fechasRelevantes;
    }


    private Set<String> extractJSONArray(JSONObject json, String key) {
        if (json == null || !json.has(key)) return Set.of();
        JSONArray array = json.optJSONArray(key);
        if (array == null) return Set.of();
        Set<String> result = new HashSet<>();
        for (int i = 0; i < array.length(); i++) {
            result.add(array.optString(i).trim());
        }
        return result;
    }
}

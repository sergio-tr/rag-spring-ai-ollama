package com.uniovi.rag.services.tools.metadata;

import com.uniovi.rag.services.retriever.ContextRetriever;
import com.uniovi.rag.services.tools.ToolExecutionContext;
import com.uniovi.rag.services.tools.ToolResult;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.text.Normalizer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import static com.uniovi.rag.utils.InfoExtractor.containsAnyKeyword;
import static com.uniovi.rag.utils.InfoExtractor.extractRelevantFragment;

public class MetadataCountAndExplainTool extends AbstractMetadataTool {

    private static final int MAX_EXAMPLES = 5;

    private static final String PROMPT_TEMPLATE = """
            El usuario quiere saber cuántas actas mencionan el siguiente tema:
            
            Pregunta: "%s"
            Número de actas encontradas: %d
            
            A continuación se muestran ejemplos representativos:
            
            %s
            
            Resume cuántas actas lo mencionan y qué se dijo. No inventes. Usa lenguaje claro y directo.
            """;

    private static final SimpleDateFormat parser = new SimpleDateFormat("d 'de' MMMM 'de' yyyy", new Locale("es", "ES"));

    public MetadataCountAndExplainTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject nerEntities = ctx.nerEntities();
        String[] keywordArray = extractKeywordsFromQuery(query).split("\\s+");

        List<String> terms = collectFilterTerms(nerEntities, keywordArray);

        List<Document> docs = retrieveAllDocuments(query);
        Map<String, List<Document>> docsByMinute = docs.stream()
                .filter(doc -> doc.getMetadata().containsKey("id"))
                .collect(Collectors.groupingBy(doc -> (String) doc.getMetadata().get("id")));

        List<AbstractMap.SimpleEntry<Date, String>> results = new ArrayList<>();

        for (Map.Entry<String, List<Document>> entry : docsByMinute.entrySet()) {
            List<Document> group = entry.getValue();
            Map<String, Object> metadata = group.getFirst().getMetadata();

            String fechaStr = (String) metadata.get("date");
            String titulo = fechaStr != null ? "Acta del " + fechaStr : "[Acta sin fecha]";
            Date fecha = parseFecha(fechaStr);

            StringBuilder fragments = new StringBuilder();

            for (Document doc : group) {
                String fragment = extractRelevantFragment(doc.getContent(), query);
                if (!fragment.isBlank() && containsAnyKeyword(normalize(fragment), terms.toArray(new String[0]))) {
                    fragments.append("- ").append(fragment).append("\n");
                }
            }

            // fallback si no hay contenido relevante
            if (fragments.isEmpty()) {
                List<String> fallbackFields = new ArrayList<>();
                fallbackFields.addAll((List<String>) metadata.getOrDefault("topics", List.of()));
                fallbackFields.addAll((List<String>) metadata.getOrDefault("section", List.of()));
                fallbackFields.add(Optional.ofNullable(metadata.get("summary")).map(Object::toString).orElse(""));

                String joined = String.join(" ", fallbackFields).toLowerCase();
                if (containsAnyKeyword(normalize(joined), terms.toArray(new String[0]))) {
                    fragments.append("- (Mención encontrada en metadatos: ").append(joined).append(")\n");
                }
            }

            if (!fragments.isEmpty()) {
                results.add(new AbstractMap.SimpleEntry<>(fecha, "**" + titulo + "**\n" + fragments.toString().strip()));
            }
        }

        if (results.isEmpty()) {
            return ToolResult.from("No se encontraron actas que traten el tema: \"" + query + "\".", getClass());
        }

        String joinedExamples = results.stream()
                .sorted(Map.Entry.comparingByKey(Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(MAX_EXAMPLES)
                .map(Map.Entry::getValue)
                .collect(Collectors.joining("\n\n"));

        String resumen = chatClient
                .prompt()
                .user(PROMPT_TEMPLATE.formatted(query, results.size(), joinedExamples))
                .call()
                .content()
                .strip();

        return ToolResult.from(resumen, getClass());
    }

    private List<String> collectFilterTerms(JSONObject nerEntities, String[] fallbackKeywords) {
        List<String> terms = new ArrayList<>();

        if (nerEntities != null) {
            JSONObject entities = nerEntities.optJSONObject("entities");
            if (entities != null) {
                JSONObject filters = entities.optJSONObject("filters");
                if (filters != null) {
                    for (String key : filters.keySet()) {
                        JSONArray values = filters.optJSONArray(key);
                        if (values != null) {
                            for (int i = 0; i < values.length(); i++) {
                                String term = normalize(values.optString(i));
                                if (!term.isBlank()) terms.add(term);
                            }
                        }
                    }
                }
            }
        }

        if (terms.isEmpty()) {
            Arrays.stream(fallbackKeywords)
                    .map(this::normalize)
                    .filter(s -> !s.isBlank())
                    .forEach(terms::add);
        }

        return terms;
    }

    private String normalize(String text) {
        return Normalizer.normalize(text == null ? "" : text.toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}", "")
                .toLowerCase()
                .trim();
    }

    private Date parseFecha(String fecha) {
        if (fecha == null) return null;
        try {
            String normalized = normalize(fecha);
            return parser.parse(normalized);
        } catch (ParseException e) {
            return null;
        }
    }
}

package com.uniovi.rag.services.tools;

import com.uniovi.rag.services.retriever.ContextRetriever;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.uniovi.rag.utils.InfoExtractor.calculateDuration;
import static com.uniovi.rag.utils.InfoExtractor.extractDate;

public class GetDurationTool extends AbstractTool {

    public GetDurationTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        List<String> fechas = extractFechasFromNER(ner);

        if (fechas.isEmpty()) {
            fechas = inferDatesFromQuery(query);
        }

        List<Document> documentos = retrieveAllDocuments(query);
        if (documentos.isEmpty()) {
            throw new RuntimeException("No se encontraron documentos relevantes.");
        }

        StringBuilder resultado = new StringBuilder();
        boolean any = false;

        for (Document doc : documentos) {
            String contenido = doc.getContent();
            String fechaDoc = extractDate(contenido);

            if (!fechas.isEmpty() && fechas.stream().noneMatch(fechaDoc::contains)) continue;

            int minutos = calculateDuration(contenido);
            if (minutos > 0) {
                int horas = minutos / 60;
                int restoMinutos = minutos % 60;

                resultado.append("La reunión del ").append(fechaDoc).append(" duró ");
                if (horas > 0) {
                    resultado.append(horas).append(" hora").append(horas != 1 ? "s" : "");
                }
                if (restoMinutos > 0) {
                    if (horas > 0) resultado.append(" y ");
                    resultado.append(restoMinutos).append(" minuto").append(restoMinutos != 1 ? "s" : "");
                }
                resultado.append(".\n");
                any = true;
            }
        }

        if (!any) {
            throw new RuntimeException("No se pudo calcular la duración de ninguna de las actas solicitadas.");
        }

        return ToolResult.from(resultado.toString().strip(), getClass());
    }

    private List<String> extractFechasFromNER(JSONObject ner) {
        if (ner == null) return List.of();
        JSONObject entidades = ner.optJSONObject("entities");
        if (entidades == null) return List.of();
        JSONObject filtros = entidades.optJSONObject("filters");
        if (filtros == null) return List.of();
        JSONArray fechasArray = filtros.optJSONArray("date");
        if (fechasArray == null) return List.of();

        return fechasArray.toList().stream().map(Object::toString).collect(Collectors.toList());
    }

    private List<String> inferDatesFromQuery(String query) {
        String prompt = """
                Dada esta pregunta de un usuario sobre actas de reuniones:
                "%s"
                
                Extrae todas las fechas mencionadas, en el formato "X de mes de Y".
                Si no hay ninguna, responde exactamente con "[]".
                
                Salida JSON: ["fecha1", "fecha2", ...]
                """.formatted(query);

        String response = chatClient
                .prompt()
                .user(prompt)
                .call()
                .content()
                .strip();

        String cleanResponse = response
                .replaceAll("```json\n", "")
                .replaceAll("\n```", "");


        try {
            JSONArray fechasJson = new JSONArray(cleanResponse);
            List<String> fechas = new ArrayList<>();
            for (int i = 0; i < fechasJson.length(); i++) {
                fechas.add(fechasJson.getString(i));
            }
            return fechas;
        } catch (Exception e) {
            throw new RuntimeException("No se pueden extraer las fechas de las actas para saber su duración");
        }
    }
}

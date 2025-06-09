package com.uniovi.rag.services.analyser;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;

import java.util.Map;

public class NERQueryAnalyser implements QueryAnalyser {

    private static final String NER_PROMPT = """
            Analiza la siguiente <pregunta> con el objetivo de extraer entidades clave que podrían estar presentes en actas de reuniones con la siguiente estructura:
            
            Estas actas siguen una estructura formal con secciones como:
            - Fecha (día, mes y año)
            - Lugar
            - Hora de inicio
            - Hora de fin
            - Lista de Asistentes: número de asistentes, nombres y cargos (por ejemplo: presidente, secretario).
            - Orden del Día: temas discutidos durante la reunión, entre los cuales se encuentran Acuerdos, Noticias, Decisiones Tomadas; resoluciones aprobadas o votadas.
            - Ruegos y Preguntas: intervenciones abiertas al final de la sesión.
            - Hora de finalización de la reunión.
            
            Extrae todas las entidades relevantes que serían útiles para encontrar la respuesta en actas de reuniones.
            Devuelve únicamente un JSON con esta estructura:
            "entities":
              "person": [],
              "filters":
                "date": [],
                "time": [],
                "place": [],
                "section": [],
                "time": [],
                "topic": []
              ,
              "answer_type": "text"
            
            No respondas a la <pregunta>. No generes explicaciones ni palabras extra. ÚNICAMENTE responde con el JSON.
            El texto debe comenzar con la llave de apertura de objeto y terminar con la llave de cierre de objeto de JSON.
            
            <Pregunta>: {pregunta}""";

    private final ChatClient chatClient;

    public NERQueryAnalyser(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public JSONObject analyse(String query) {
        String prompt = new PromptTemplate(NER_PROMPT).create(Map.of("pregunta", query)).getContents();

        String response = chatClient
                .prompt()
                .system("Eres un extractor de entidades en JSON. Tu única salida será un objeto JSON " +
                        "que  debe comenzar con la llave de apertura de objeto y terminar con la llave de cierre de objeto de JSON.")
                .user(prompt)
                .call()
                .content();

        String cleanResponse = response
                .replaceAll("(?s)```.*?\\n", "")  // remueve ```json\n o similares
                .replaceAll("```", "")
                .replaceAll("'", "")
                .strip();

        log().info("NER-QUERY: JSON devuelto →\n{}", cleanResponse);

        try {
            if (!cleanResponse.trim().startsWith("{")) {
                throw new IllegalArgumentException("Respuesta no es un JSON válido.");
            }

            JSONObject json = new JSONObject(cleanResponse);
            validate(json);
            return json;
        } catch (Exception e) {
            log().warn("NER: Error parsing JSON para query '{}': {}", query, e.getMessage());
            return fallback();
        }
    }

    private void validate(JSONObject json) {
        if (!json.has("entities")) {
            json.put("entities", new JSONObject());
        }

        JSONObject entidades = json.getJSONObject("entities");

        if (!entidades.has("person") || !(entidades.get("person") instanceof JSONArray)) {
            entidades.put("person", new JSONArray());
        }

        if (!entidades.has("filters")) {
            entidades.put("filters", new JSONObject());
        }

        JSONObject filtros = entidades.getJSONObject("filters");

        for (String key : new String[]{"date", "place", "section", "time", "topic"}) {
            if (!filtros.has(key) || !(filtros.get(key) instanceof JSONArray)) {
                filtros.put(key, new JSONArray());
            }
        }

        if (!entidades.has("answer_type") || !(entidades.get("answer_type") instanceof String)) {
            entidades.put("answer_type", "desconocido");
        }
    }

    private JSONObject fallback() {
        return new JSONObject("""
                    {
                      "entities": {
                        "person": [],
                        "filters": {
                          "date": [],
                          "time": [],
                          "place": [],
                          "section": [],
                          "topic": []
                        },
                        "answer_type": "unknown"
                      }
                    }
                """);
    }
}

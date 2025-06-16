package com.uniovi.rag.services.analyser;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;

import java.util.Map;

public class NERQueryAnalyser implements QueryAnalyser {

    private static final String NER_PROMPT = """
    Analiza la siguiente <pregunta> para extraer entidades clave que puedan estar presentes en actas de reuniones de vecinos. 
    Devuelve SOLO un objeto JSON con los siguientes campos (rellena solo los relevantes para la pregunta, el resto déjalos como array vacío):

    - date: Fechas o referencias temporales (ej: "25 de febrero de 2026", "última reunión").
    - place: Lugar donde se celebró la reunión.
    - startTime: Hora de inicio de la reunión.
    - endTime: Hora de fin de la reunión.
    - president: Persona que presidió la reunión.
    - secretary: Persona que actuó como secretario/a.
    - attendees: Nombres de asistentes mencionados en la pregunta.
    - numberOfAttendees: Números o referencias a la cantidad de asistentes.
    - agenda: Puntos del día o temas concretos sobre los que se pregunta (por ejemplo: "aprobación de cuentas", "ruegos y preguntas").
    - decisions: Decisiones o acuerdos explícitos mencionados en la pregunta (ej: "se aprobó el presupuesto").
    - mentionedEntities: Empresas, organismos, técnicos u otras entidades mencionadas.
    - topics: Temas generales tratados (ej: "seguridad", "iluminación").
    - section: Sección del acta sobre la que se pregunta (ej: "asistentes", "acuerdos", "ruegos y preguntas").
    - summary: Si la pregunta pide un resumen, indica el tipo de resumen solicitado (ej: "resumen de la reunión").
    - answer_type: Tipo de respuesta esperada ("person", "number", "text", "date", "decision", "topic", etc.).

    Ejemplos:
    Pregunta: "¿Quién fue el presidente en la reunión del 25 de febrero de 2026?"
    Respuesta:
    {
      "date": ["25 de febrero de 2026"],
      "president": [],
      "answer_type": "person"
    }

    Pregunta: "¿Cuántos asistentes hubo en la última reunión?"
    Respuesta:
    {
      "numberOfAttendees": [],
      "date": ["última"],
      "answer_type": "number"
    }

    Pregunta: "¿Qué se decidió sobre la calefacción?"
    Respuesta:
    {
      "decisions": ["calefacción"],
      "answer_type": "decision"
    }

    Pregunta: "Resume la reunión del 25 de febrero de 2026"
    Respuesta:
    {
      "date": ["25 de febrero de 2026"],
      "summary": ["resumen de la reunión"],
      "answer_type": "text"
    }

    Si no hay información para un campo, déjalo como array vacío. Devuelve ÚNICAMENTE el JSON, sin explicaciones ni formato adicional.
    <Pregunta>: {pregunta}
    """;

    private final ChatClient chatClient;

    public NERQueryAnalyser(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public JSONObject analyse(String query) {
        String prompt = new PromptTemplate(NER_PROMPT).create(Map.of("pregunta", query)).getContents();

        String response = chatClient
                .prompt()
                .system( """
                    Eres un extractor de entidades en JSON de actas de reuniones de vecinos con el siguiente formato.
                    Estas actas siguen una estructura formal con secciones como:
                - Fecha (día, mes y año)
                - Lugar
                - Hora de inicio
                - Hora de fin
                - Lista de Asistentes: número de asistentes, nombres y cargos (por ejemplo: presidente, secretario).
                - Orden del Día: temas discutidos durante la reunión, entre los cuales se encuentran Acuerdos, Noticias, Decisiones Tomadas; resoluciones aprobadas o votadas.
                - Ruegos y Preguntas: intervenciones abiertas al final de la sesión.
                - Hora de finalización de la reunión.
                Tu única salida será un objeto JSON que debe comenzar con la llave de apertura de objeto y terminar con la llave de cierre de objeto de JSON.
                """)
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
        // Lista de todos los campos esperados según Minute y el prompt
        String[] fields = {"date", "place", "startTime", "endTime", "president", "secretary", "attendees", "numberOfAttendees", "agenda", "decisions", "mentionedEntities", "topics", "section", "summary", "answer_type"};
        for (String field : fields) {
            if (!json.has(field)) {
                if (field.equals("answer_type")) {
                    json.put(field, "unknown");
                } else {
                    json.put(field, new JSONArray());
                }
            } else {
                // Si el campo no es answer_type, asesgurarse de que es un array
                if (!field.equals("answer_type") && !(json.get(field) instanceof JSONArray)) {
                    json.put(field, new JSONArray());
                }
            }
        }
    }

    private JSONObject fallback() {
        return new JSONObject("""
                {
                  "date": [],
                  "place": [],
                  "startTime": [],
                  "endTime": [],
                  "president": [],
                  "secretary": [],
                  "attendees": [],
                  "numberOfAttendees": [],
                  "agenda": [],
                  "decisions": [],
                  "mentionedEntities": [],
                  "topics": [],
                  "section": [],
                  "summary": [],
                  "answer_type": "unknown"
                }
            """);
    }
}

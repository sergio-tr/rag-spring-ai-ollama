package com.uniovi.rag.services.query;

import com.uniovi.rag.services.analyzer.QueryAnalyser;
import com.uniovi.rag.services.classifier.SimpleQueryClassifier;
import com.uniovi.rag.services.expand.QueryExpander;
import com.uniovi.rag.services.retriever.ContextRetriever;
import org.json.JSONObject;
import org.springframework.ai.ollama.OllamaChatModel;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public abstract class AbstractProcessQueryService implements QueryService {

    protected static final String PROMPT_TEMPLATE = "La siguiente información ya ha sido extraída como respuesta directa a la pregunta \"%s\". " +
            "Tu única tarea es presentarla en forma de respuesta clara y breve en español. " +
            "No debes cuestionar, verificar ni rechazar la información. No añadas contexto adicional, ni justificaciones, ni comentarios.\n\n" +
            "Datos extraídos:\n%s";

    protected final OllamaChatModel chatModel;
    protected final QueryExpander expander;
    protected final SimpleQueryClassifier classifier;
    protected final QueryAnalyser analyser;
    protected final ContextRetriever retriever;
    protected String systemPrompt= """
        Responde siempre en español. A continuación, tienes información clave para interpretar las preguntas y generar respuestas correctas.
        Presta especial atención a los elementos de contexto para asegurar precisión en las respuestas.
        
        """;

    protected AbstractProcessQueryService(QueryExpander expander, SimpleQueryClassifier classifier, QueryAnalyser analyser, ContextRetriever retriever, OllamaChatModel chatModel) {
        this.chatModel = chatModel;
        this.expander = expander;
        this.classifier = classifier;
        this.analyser = analyser;
        this.retriever = retriever;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    protected String askQueryToLlama(String query) {
        try {
            // URL del endpoint de Ollama para hacer la consulta con el endpoint /generate
            URL url = new URL("http://localhost:11434/api/generate"); // Endpoint para el modelo de generación
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            // Crear JSON con el modelo y la consulta (query)
            JSONObject json = new JSONObject();
            json.put("model", "llama3.2:3b"); // Especificamos el modelo a usar
            json.put("stream", false);
            json.put("prompt", query); // El texto de la consulta
            json.put("max_tokens", 131072); // Limitar la longitud de la respuesta (opcional)

            // Enviar petición a Ollama
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = json.toString().getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            // Leer respuesta de Ollama
            Scanner scanner = new Scanner(conn.getInputStream(), "utf-8");
            String response = scanner.useDelimiter("\\A").next();
            scanner.close();

            // Parsear la respuesta JSON
            JSONObject jsonResponse = new JSONObject(response);
            System.out.println(jsonResponse);
            String generatedText = jsonResponse.getString("response"); // Obtener el texto generado bajo la clave "response"

            return generatedText; // Retornar la respuesta del modelo

        } catch (Exception e) {
            e.printStackTrace();
            return null; // En caso de error, retornar null
        }
    }
    
}

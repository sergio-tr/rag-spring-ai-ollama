package com.uniovi.rag.services.query;

import com.uniovi.rag.services.analyser.QueryAnalyser;
import com.uniovi.rag.services.expand.QueryExpander;
import com.uniovi.rag.services.retriever.ContextRetriever;
import org.json.JSONObject;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.stereotype.Service;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import java.util.List;
import org.springframework.ai.document.Document;
import java.net.URI;

@Service
public class SimpleQueryService implements QueryService {

    protected static final String PROMPT_TEMPLATE = """
        The following information has already been extracted as a direct answer to the question \"%s\".
        Your only task is to present it as a clear and concise response in Spanish.
        You must not question, verify, or reject the information. Do not add any additional context, justifications, or comments.
        Extracted data:
        %s""";

    protected final OllamaChatModel chatModel;
    protected final QueryExpander expander;
    protected final QueryAnalyser analyser;
    protected final ContextRetriever retriever;

    public SimpleQueryService(QueryExpander expander, QueryAnalyser analyser, ContextRetriever retriever, OllamaChatModel chatModel) {
        this.chatModel = chatModel;
        this.expander = expander;
        this.analyser = analyser;
        this.retriever = retriever;
    }

    protected String askQueryToLlama(String query) {
        try {
            // URL del endpoint de Ollama para hacer la consulta con el endpoint /generate
            URL url = URI.create("http://localhost:11434/api/generate").toURL(); // Endpoint para el modelo de generación
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
            log().debug(jsonResponse.toString());
            String generatedText = jsonResponse.getString("response"); // Obtener el texto generado bajo la clave "response"

            return generatedText; // Retornar la respuesta del modelo

        } catch (Exception e) {
            e.printStackTrace();
            return null; // En caso de error, retornar null
        }
    }

    protected static int countTokens(String text) {
        try {
            URL url = URI.create("http://localhost:11434/api/embeddings").toURL(); // Ollama usa este endpoint
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            // Crear JSON con el modelo y el texto
            JSONObject json = new JSONObject();
            json.put("model", "llama3.2:3b");
            json.put("prompt", text);

            // Enviar petición a Ollama
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = json.toString().getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            // Leer respuesta de Ollama
            Scanner scanner = new Scanner(conn.getInputStream(), "utf-8");
            String response = scanner.useDelimiter("\\A").next();
            scanner.close();

            // Extraer el número de tokens desde la respuesta JSON
            JSONObject jsonResponse = new JSONObject(response);
            return jsonResponse.getJSONArray("embedding").length(); // El tamaño de embedding es el número de tokens

        } catch (Exception e) {
            e.printStackTrace();
            return -1; // Error
        }
    }

    public String generateResponse(String question) {
        if (question == null || question.trim().isEmpty()) {
            throw new IllegalArgumentException("La pregunta no puede ser nula, vacia o solo espacios en blanco.");
        }

        String expandedQuery = expander.expand(question);

        JSONObject nerEntities = analyser.analyse(question);

        List<Document> docs = retriever.retrieve(expandedQuery);
        String context = retriever.createContext(docs, expandedQuery, nerEntities);

        String template = String.format(
                PROMPT_TEMPLATE,
                question, context
        );

        log().debug("\n\n-----------------------------------------------------------------------------");
        log().debug("-----------------------------------------------------------------------------");
        log().debug("QUERY: Pregunta final: " + template);
        log().debug("\n\n-----------------------------------------------------------------------------");
        log().debug("-----------------------------------------------------------------------------");

        return askQueryToLlama(template);
    }

}
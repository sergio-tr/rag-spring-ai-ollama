package com.uniovi.rag.services.query;

import com.uniovi.rag.services.analyser.QueryAnalyser;
import com.uniovi.rag.services.expand.QueryExpander;
import com.uniovi.rag.services.retriever.AbstractContextRetriever;
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
import com.uniovi.rag.model.QueryResponse;

@Service
public class SimpleQueryService implements QueryService {

    private static final String QUESTION_PLACEHOLDER = "__QUESTION__";
    private static final String CONTEXT_PLACEHOLDER = "__CONTEXT__";
    protected static final String PROMPT_TEMPLATE = "You are a helpful assistant that answers questions based on retrieved documents from a meeting minutes database. Base your answer ONLY on the information provided in the context below. RULES: If the context is empty or does not contain enough information, clearly state that you cannot find the information. DO NOT invent, guess, or make up information. NEVER invent names, dates, places, actas, or any other information not explicitly in the context. Answer in the SAME LANGUAGE as the user's question. Be concise. Do not repeat the question. Question: " + QUESTION_PLACEHOLDER + " Context: " + CONTEXT_PLACEHOLDER + " Provide your direct answer now:";

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
            log().info(jsonResponse.toString());
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

    public QueryResponse generateResponse(String question) {
        if (question == null || question.trim().isEmpty()) {
            throw new IllegalArgumentException("La pregunta no puede ser nula, vacia o solo espacios en blanco.");
        }

        String expandedQuery = expander.expand(question);

        JSONObject nerEntities = analyser.analyse(question);

        List<Document> docs;
        if (retriever instanceof AbstractContextRetriever && nerEntities != null && !nerEntities.isEmpty()) {
            docs = ((AbstractContextRetriever) retriever).retrieveWithMetadataFilters(expandedQuery, nerEntities);
        } else {
            docs = retriever.retrieve(expandedQuery);
        }
        String context = retriever.createContext(docs, expandedQuery, nerEntities);

        if (context == null || context.trim().isEmpty()) {
            return QueryResponse.fromLLM("No se encontró información relevante en los documentos disponibles para responder a esta pregunta.");
        }

        String template = PROMPT_TEMPLATE
                .replace(QUESTION_PLACEHOLDER, question)
                .replace(CONTEXT_PLACEHOLDER, context);

        log().info("\n\n-----------------------------------------------------------------------------");
        log().info("-----------------------------------------------------------------------------");
        log().info("QUERY: Pregunta final: " + template);
        log().info("\n\n-----------------------------------------------------------------------------");
        log().info("-----------------------------------------------------------------------------");

        return QueryResponse.fromLLM(askQueryToLlama(template));
    }

}
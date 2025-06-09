package com.uniovi.rag.services.query;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagToolsConfiguration;
import com.uniovi.rag.services.analyser.QueryAnalyser;
import com.uniovi.rag.services.classifier.QueryClassifier;
import com.uniovi.rag.services.classifier.QueryType;
import com.uniovi.rag.services.expand.QueryExpander;
import com.uniovi.rag.services.retriever.ContextRetriever;
import com.uniovi.rag.services.tools.Tool;
import com.uniovi.rag.services.tools.ToolExecutionContext;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class SimpleProcessQueryService implements QueryService {

    protected static final String PROMPT_TEMPLATE = "La siguiente información en <contexto> ya ha sido extraída como respuesta directa a la <pregunta>" +
            "Tu única tarea es presentarla en forma de respuesta clara y breve en español. " +
            "No debes cuestionar, verificar ni rechazar la información. No añadas contexto adicional, ni justificaciones, ni comentarios.\n" +
            "<Pregunta> %s </Pregunta>\n" +
            "<Contexto> %s </Contexto>";


    protected final RagFeatureConfiguration featureConfig;
    protected final ChatClient chatClient;
    protected final QueryExpander expander;
    protected final QueryAnalyser analyser;
    protected final QueryClassifier classifier;
    protected final ContextRetriever retriever;
    protected final RagToolsConfiguration toolsConfig;

    public SimpleProcessQueryService(RagFeatureConfiguration featureConfig,
                                     RagToolsConfiguration toolsConfig,
                                     QueryExpander expander,
                                     QueryAnalyser analyser,
                                     QueryClassifier classifier,
                                     ContextRetriever retriever,
                                     ChatClient chatClient) {
        this.featureConfig = featureConfig;
        this.chatClient = chatClient;
        this.expander = expander;
        this.analyser = analyser;
        this.classifier = classifier;
        this.retriever = retriever;
        this.toolsConfig = toolsConfig;
    }

    @Override
    public String generateResponse(String query) {
        String finalQuery = featureConfig.isExpansionEnabled() ? expander.expand(query) : query;
        JSONObject nerEntities = featureConfig.isNerEnabled() ? analyser.analyse(finalQuery) : null;
        QueryType queryType = featureConfig.isToolsEnabled() ? classifier.classify(finalQuery) : null;

        if (queryType != null) {
            Tool tool = toolsConfig.getTool(queryType);

            try {
//                String toolResponse = featureConfig.isNerEnabled() ? tool.execute(finalQuery, nerEntities) : tool.execute(finalQuery);
                String toolResponse = tool.execute(ToolExecutionContext.of(finalQuery, queryType, nerEntities)).result();
                if (toolResponse != null) {
                    return toolResponse;
                }
            } catch (Exception e) {
                System.err.println(e.getMessage());
            }

        }

        return askModel(finalQuery, nerEntities);

    }

    private String askModel(String query, JSONObject nerEntities) {
        String context = retriever.createContext(retriever.retrieve(query), query, nerEntities);
        String prompt = String.format(PROMPT_TEMPLATE, query, context);
        return chatClient
                .prompt()
                .user(prompt)
                .call()
                .content();
    }

//    protected String askQueryToLlama(String query) {
//        try {
//            // URL del endpoint de Ollama para hacer la consulta con el endpoint /generate
////            URL url = new URL("http://localhost:11434/api/generate"); // Endpoint para el modelo de generación
//            URL url = new URL("http://156.35.95.18:11434/api/generate");
//            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
//            conn.setRequestMethod("POST");
//            conn.setRequestProperty("Content-Type", "application/json");
//            conn.setDoOutput(true);
//
//            // Crear JSON con el modelo y la consulta (query)
//            JSONObject json = new JSONObject();
//            json.put("model", "llama3.2:latest"); // Especificamos el modelo a usar
//            json.put("stream", false);
//            json.put("prompt", query); // El texto de la consulta
//            json.put("max_tokens", 131072); // Limitar la longitud de la respuesta (opcional)
//
//            // Enviar petición a Ollama
//            try (OutputStream os = conn.getOutputStream()) {
//                byte[] input = json.toString().getBytes("utf-8");
//                os.write(input, 0, input.length);
//            }
//
//            // Leer respuesta de Ollama
//            Scanner scanner = new Scanner(conn.getInputStream(), "utf-8");
//            String response = scanner.useDelimiter("\\A").next();
//            scanner.close();
//
//            // Parsear la respuesta JSON
//            JSONObject jsonResponse = new JSONObject(response);
//            System.out.println(jsonResponse);
//            String generatedText = jsonResponse.getString("response"); // Obtener el texto generado bajo la clave "response"
//
//            return generatedText; // Retornar la respuesta del modelo
//
//        } catch (Exception e) {
//            e.printStackTrace();
//            return null; // En caso de error, retornar null
//        }
//    }
//
//    protected static int countTokens(String text) {
//        try {
//            URL url = new URL("http://localhost:11434/api/embeddings"); // Ollama usa este endpoint
//            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
//            conn.setRequestMethod("POST");
//            conn.setRequestProperty("Content-Type", "application/json");
//            conn.setDoOutput(true);
//
//            // Crear JSON con el modelo y el texto
//            JSONObject json = new JSONObject();
//            json.put("model", "llama3.2:latest");
//            json.put("prompt", text);
//
//            // Enviar petición a Ollama
//            try (OutputStream os = conn.getOutputStream()) {
//                byte[] input = json.toString().getBytes("utf-8");
//                os.write(input, 0, input.length);
//            }
//
//            // Leer respuesta de Ollama
//            Scanner scanner = new Scanner(conn.getInputStream(), "utf-8");
//            String response = scanner.useDelimiter("\\A").next();
//            scanner.close();
//
//            // Extraer el número de tokens desde la respuesta JSON
//            JSONObject jsonResponse = new JSONObject(response);
//            return jsonResponse.getJSONArray("embedding").length(); // El tamaño de embedding es el número de tokens
//
//        } catch (Exception e) {
//            e.printStackTrace();
//            return -1; // Error
//        }
//    }
}



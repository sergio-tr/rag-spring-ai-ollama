package com.uniovi.rag.services.query;

import com.uniovi.rag.services.analyzer.QueryAnalyser;
import com.uniovi.rag.services.classifier.SimpleQueryClassifier;
import com.uniovi.rag.services.classifier.QueryType;
import com.uniovi.rag.services.expand.QueryExpander;
import com.uniovi.rag.services.retriever.ContextRetriever;
import org.springframework.ai.ollama.OllamaChatModel;

import java.util.Map;

public class SimpleProcessQueryService extends AbstractProcessQueryService {

    private final Map<QueryType, ContextRetriever> retrieversByType;

    protected static final String PROMPT_TEMPLATE = "La siguiente <Información> ya ha sido extraída como respuesta directa a la <Pregunta> \"%s\". " +
            "Tu única tarea es presentarla en forma de respuesta clara y breve en español. " +
            "No debes cuestionar, verificar ni rechazar la información. No añadas contexto adicional, ni justificaciones, ni comentarios.\n\n" +
            "<Información>:\n%s";

    public SimpleProcessQueryService(
            QueryExpander expander,
            SimpleQueryClassifier classifier,
            QueryAnalyser analyser,
            OllamaChatModel chatModel,
            Map<QueryType, ContextRetriever> retrieversByType
    ) {
        super(expander, classifier, analyser, null, chatModel); // retriever se gestiona por tipo
        this.retrieversByType = retrieversByType;
    }

    @Override
    public String generateResponse(String question) {
        if (question == null || question.trim().isEmpty()) {
            throw new IllegalArgumentException("La pregunta no puede ser nula, vacía o solo espacios en blanco.");
        }

        String expandedQuery = expander.expand(question);

        QueryType type = classifier.classify(question);

        String nerEntities = analyser.analyse(question);

        ContextRetriever selectedRetriever = retrieversByType.getOrDefault(
                type, retrieversByType.get(QueryType.CONTENT)
        );

        if(selectedRetriever == null){
            selectedRetriever = retrieversByType.get(QueryType.CONTENT);
        }

        String context = selectedRetriever.retrieve(expandedQuery, nerEntities);

        String template = String.format(PROMPT_TEMPLATE, question, context);

        System.out.println("\n\n-----------------------------------------------------------------------------");
        System.out.println("-----------------------------------------------------------------------------");
        System.out.println("QUERY: Pregunta final: " + template);
        System.out.println("TIPO: " + type.name());
        System.out.println("\n\n-----------------------------------------------------------------------------");
        System.out.println("-----------------------------------------------------------------------------");

        return askQueryToLlama(template);
    }
}

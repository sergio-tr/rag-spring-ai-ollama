package com.uniovi.rag.services.query;

import com.uniovi.rag.services.analyzer.QueryAnalyser;
import com.uniovi.rag.services.expand.QueryExpander;
import com.uniovi.rag.services.retriever.ContextRetriever;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.stereotype.Service;

@Service
public class ComplexQueryService extends AbstractQueryService {

    public ComplexQueryService(QueryExpander expander, QueryAnalyser analyzer, ContextRetriever retriever, OllamaChatModel model) {
        super(expander, analyzer,  retriever, model);
    }

    public String generateResponse(String question) {
        if (question == null || question.trim().isEmpty()) {
            throw new IllegalArgumentException("La pregunta no puede ser nula, vacia o solo espacios en blanco.");
        }

        String expandedQuery = expander.expand(question);

        String nerEntities = analyzer.analyze(question);

        String context = retriever.retrieve(expandedQuery, nerEntities);

        String template = String.format(
                PROMPT_TEMPLATE,
                question, context
        );

        System.out.println("\n\n-----------------------------------------------------------------------------");
        System.out.println("-----------------------------------------------------------------------------");
        System.out.println("QUERY: Pregunta final: " + template);
        System.out.println("\n\n-----------------------------------------------------------------------------");
        System.out.println("-----------------------------------------------------------------------------");

        return askQueryToLlama(template);
    }

//    private List<Document> filterRelevantDocs(List<Document> docs) {
//        return docs.stream()
//                .filter(doc -> isRelevant(doc.getContent()))
//                .collect(Collectors.toList());
//    }
//
//    private boolean isRelevant(String content) {
//        String relevancePrompt = String.format(
//                "Analiza la relación entre la siguiente pregunta y el contenido del documento.\n" +
//                "Pregunta: \"%s\"\n" +
//                "Contenido:\"%s\"\n" +
//                "Si el contenido puede tener información útil para responder la pregunta, devuelve únicamente el número '1'. " +
//                "Si no tiene absolutamente ninguna relación con la pregunta, devuelve únicamente el número '0'. No devuelvas nada más.",
//                question, content
//        );
//
//        String response = chatModel.call(relevancePrompt).trim();
//        return response.equals("1");
//    }
}

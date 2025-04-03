package com.uniovi.rag.services.retriever;

import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;

import java.util.List;
import java.util.stream.Collectors;

public class FilteredContextRetriever extends AbstractContextRetriever {

    protected String promptTemplate =
            "Tu tarea es filtrar el siguiente contenido eliminando solo información irrelevante " +
            "pero sin modificar aquello que filtres por su importancia en relación a la pregunta. " +
            " Mantén toda la información clave que pueda ayudar a responder la pregunta sin cambiar su significado." +
            " No hagas inferencias ni afirmaciones nuevas, solo devuelve el contenido ajustado." +
            "Si no hay información relevante, simplemente devuelve ''.\n" +
            "Contenido:\"%s\"\n" +
            "Pregunta: \"%s\"\n" +
            "Devuelve el contenido filtrado sin añadir ningún comentario extra";

    public FilteredContextRetriever(PgVectorStore vectorStore, OllamaChatModel chatModel, int topK) {
        super(vectorStore, chatModel, topK);
    }

    @Override
    public String retrieve(String query) {
        SearchRequest req = SearchRequest.query(query).withTopK(topK);
        List<Document> retrievedDocs = vectorStore.similaritySearch(req);

        return retrievedDocs.stream()
                .map(doc -> filterContentByQuestion(doc, query))
                .collect(Collectors.joining("\n"));
    }

    protected void setPromptTemplate(String promptTemplate) {
        this.promptTemplate = promptTemplate;
    }

    private String filterContentByQuestion(Document doc, String query) {

        String filterPrompt = String.format(
                promptTemplate,
                doc.getContent(), query
        );

        System.out.println("\n\n-----------------------------------------------------------------------------");
        System.out.println("RETRIEVER: Contenido original: " + doc.getContent());

        String filteredContent = model.call(filterPrompt);

        System.out.println("\n\n-----------------------------------------------------------------------------");
        System.out.println("RETRIVER: Contenido filtrado: " + filteredContent);
        System.out.println("------------------------------------------------------------------------------");
        return filteredContent;
    }
}

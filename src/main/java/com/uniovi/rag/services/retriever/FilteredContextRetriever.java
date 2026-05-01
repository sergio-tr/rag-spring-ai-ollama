package com.uniovi.rag.services.retriever;

import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.vectorstore.PgVectorStore;

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

    protected String nerPromptTemplate =
            "Tu tarea es filtrar el siguiente <Contenido> eliminando solo información irrelevante " +
            "pero sin modificar aquello que filtres por su importancia en relación a la <Pregunta>. " +
            "Mantén toda la información clave que pueda ayudar a responder la pregunta sin cambiar su significado." +
            "No hagas inferencias ni afirmaciones nuevas, solo devuelve el contenido ajustado en el contexto necesario." +
            "Si no hay información relevante, simplemente devuelve ''.\n" +
            "Entidades a tener en cuenta para la recuperación de información del <Contenido>: \"%s\"\n" +
            "<Pregunta>: \"%s\"\n" +
            "<Contenido>:\"%s\"\n" +
            "Devuelve el contenido filtrado sin añadir automáticamente ningúna otra información";

    public FilteredContextRetriever(PgVectorStore vectorStore, OllamaChatModel chatModel, int topK) {
        super(vectorStore, chatModel, topK);
    }

    protected void setPromptTemplate(String promptTemplate) {
        this.promptTemplate = promptTemplate;
    }

    protected void setNerPromptTemplate(String nerPromptTemplate) {
        this.nerPromptTemplate = nerPromptTemplate;
    }

    @Override
    protected String filterContentByQuestion(Document doc, String query) {

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

    @Override
    protected String filterContentByQuestion(Document doc, String query, String context) {
        String filterPrompt = String.format(
                promptTemplate,
                doc.getContent(), query
        );

        System.out.println("\n\n-----------------------------------------------------------------------------");
        System.out.println("NER RETRIEVER: Contenido original: " + doc.getContent());

        String filteredContent = model.call(filterPrompt);

        System.out.println("\n\n-----------------------------------------------------------------------------");
        System.out.println("NER RETRIEVER: Contenido filtrado: " + filteredContent);
        System.out.println("------------------------------------------------------------------------------");
        return filteredContent;
    }
}

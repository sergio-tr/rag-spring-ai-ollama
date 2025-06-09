package com.uniovi.rag.services.retriever;

import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.PgVectorStore;

public class FilteredContextRetriever extends AbstractContextRetriever {

    protected String promptTemplate =
            "Tu tarea es filtrar el siguiente <contenido> eliminando solo información irrelevante " +
                    "pero sin modificar aquello que filtres por su importancia en relación a la <pregunta>. " +
                    " Mantén toda la información clave que pueda ayudar a responder la <pregunta> sin cambiar su significado." +
                    " No hagas inferencias ni afirmaciones nuevas, solo devuelve el <contenido> ajustado." +
                    "Si no hay información relevante, simplemente devuelve ''.\n" +
                    "<Contenido>:\"%s\"\n" +
                    "<Pregunta>: \"%s\"\n" +
                    "Devuelve el contenido filtrado sin añadir ningún comentario extra";

    protected String nerPromptTemplate =
            "Tu tarea es filtrar el siguiente <Contenido> eliminando solo información irrelevante " +
                    "pero sin modificar aquello que filtres por su importancia en relación a la <Pregunta>. " +
                    "Mantén toda la información clave que pueda ayudar a responder la <pregunta> sin cambiar su significado." +
                    "No hagas inferencias ni afirmaciones nuevas, solo devuelve el <contenido> ajustado en el contexto necesario." +
                    "Si no hay información relevante, simplemente devuelve ''.\n" +
                    "<Contenido>:\"%s\"\n" +
                    "<Pregunta>: \"%s\"\n" +
                    "Entidades a tener en cuenta para la recuperación de información del <Contenido>: \"%s\"\n" +
                    "Devuelve el contenido filtrado sin añadir automáticamente ningúna otra información";

    public FilteredContextRetriever(PgVectorStore vectorStore, ChatClient chatClient, int topK, double similarityThreshold) {
        super(vectorStore, chatClient, topK, similarityThreshold);
    }

    protected void setPromptTemplate(String promptTemplate) {
        this.promptTemplate = promptTemplate;
    }

    protected void setNerPromptTemplate(String nerPromptTemplate) {
        this.nerPromptTemplate = nerPromptTemplate;
    }

    @Override
    public String filterDocumentContent(Document doc, String query, JSONObject entities) {

        String filterPrompt = entities == null ?
                String.format(
                        promptTemplate,
                        doc.getContent(), query
                ) :
                String.format(
                        nerPromptTemplate,
                        doc.getContent(), query, entities
                );

        System.out.println("\n\n-----------------------------------------------------------------------------");
        System.out.println("FILTERED RETRIEVER: Contenido original: " + doc.getContent());

        String filteredContent = chatClient.prompt().user(filterPrompt).call().content();

        System.out.println("\n\n-----------------------------------------------------------------------------");
        System.out.println("FILTERED RETRIEVER: Contenido filtrado: " + filteredContent);
        System.out.println("------------------------------------------------------------------------------");
        return filteredContent;
    }
}

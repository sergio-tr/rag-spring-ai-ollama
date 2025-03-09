package com.uniovi.rag.services.query;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.stereotype.Service;

@Service
public abstract class AbstractQueryService implements QueryService {

    protected final OllamaChatModel chatModel;
    protected final PgVectorStore vectorStore;

    public AbstractQueryService(PgVectorStore vectorStore, OllamaChatModel chatModel) {
        this.chatModel = chatModel;
        this.vectorStore = vectorStore;
    }

    protected String systemPrompt= """
            
            Responde siempre en español. A continuación, tienes información clave para interpretar las preguntas y generar respuestas correctas.
            Presta especial atención a los elementos de contexto para asegurar precisión en las respuestas.
            
            """;

    protected PromptTemplate promptTemplate = new PromptTemplate("""
            Responde a la PREGUNTA especificada a continuación utilizando el contexto dado por los DOCUMENTOS.
            Trata de analizar bien el contexto dado en DOCUMENTOS para proporcionar respuestas precisas y relevantes.
            Si no estás seguro o si la respuesta no se encuentra en el apartado DOCUMENTOS, simplemente indica que no conoces la respuesta.

            PREGUNTA: {query}

            DOCUMENTOS: {documents}
            """);

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public void setPromptTemplate(PromptTemplate promptTemplate) {
        this.promptTemplate = promptTemplate;
    }
}

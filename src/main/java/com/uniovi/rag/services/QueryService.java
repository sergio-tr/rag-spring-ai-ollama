package com.uniovi.rag.services;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class QueryService {
    private final PgVectorStore vectorStore;
    private final OllamaChatModel chatModel;

    private String systemPrompt = """
            
            Responde siempre en español. A continuación, tienes información clave para interpretar las preguntas y generar respuestas correctas.
            Presta especial atención a los elementos de contexto para asegurar precisión en las respuestas.
            
            """;
    private final static PromptTemplate PROMPT_TEMPLATE = new PromptTemplate("""
                
            Eres un asistente útil, conversando con un usuario sobre los temas contenidos en un conjunto de documentos.
            Utiliza la información del apartado DOCUMENTOS para proporcionar respuestas precisas. Si no estás seguro o si la
            respuesta no se encuentra en el apartado DOCUMENTOS, simplemente indica que no conoces la respuesta.
                        
            PREGUNTA: {query}
                        
            DOCUMENTOS: {documents}
            
            """);

    public QueryService(PgVectorStore vectorStore, OllamaChatModel chatModel) {
        this.chatModel = chatModel;
        this.vectorStore = vectorStore;
    }
    public String generateResponse(String question) {
        List<Document> relevantDocs = vectorStore.similaritySearch(question);

        String context = relevantDocs.stream()
                .map(Document::getContent)
                .reduce("", (a, b) -> a + "\n\n" + b);

        Message prompt = PROMPT_TEMPLATE.createMessage(Map.of("query", question, "documents", context));
        UserMessage userMessage = new UserMessage(String.valueOf(prompt));
        SystemMessage systemMessage = new SystemMessage(systemPrompt);

        return chatModel.call(systemMessage, userMessage);
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }
}
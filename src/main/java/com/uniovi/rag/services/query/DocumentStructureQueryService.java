package com.uniovi.rag.services.query;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class DocumentStructureQueryService extends AbstractQueryService {
    private final static String DOCUMENT_STRUCTURE_PROMPT = """
            Para interpretar correctamente las actas de reunión, considera la siguiente estructura:
            
            - Encabezado: Contiene la fecha, lugar, hora de inicio y finalización.
            - Lista de Asistentes: Enumera a los participantes, indicando roles específicos como presidente o secretario.
            - Orden del Día: Presenta los temas discutidos en la reunión.
            - Decisiones Tomadas: Registra acuerdos, resoluciones y votaciones realizadas.
            - Ruegos y Preguntas: Incluye comentarios adicionales o temas pendientes.
            
            Reformula la consulta del usuario para alinearla con esta estructura y mejorar la recuperación de información.
            Trata de enmarcar la pregunta en una o varias de las secciones reconocidas de cada documento.
            """;

    private final static PromptTemplate PROMPT_TEMPLATE = new PromptTemplate("""
            Responde a la PREGUNTA DEL USUARIO especificada a continuación utilizando el contexto dado por los DOCUMENTOS.
            Trata de analizar bien el contexto dado en DOCUMENTOS para proporcionar respuestas precisas y relevantes.
            Si no estás seguro o si la respuesta no se encuentra en el apartado DOCUMENTOS, simplemente indica que no conoces la respuesta.
            La PREGUNTA ha sido reformulado teniendo en cuenta la estructura de todos los DOCUMENTOS.
            
            DOCUMENTOS: {documents}
            
            PREGUNTA DEL USUARIO (Reformulada según la estructura de los documentos): {query}
            """);

    public DocumentStructureQueryService(PgVectorStore vectorStore, OllamaChatModel chatModel) {
        super(vectorStore, chatModel);
        setPromptTemplate(PROMPT_TEMPLATE);
    }

    public String generateResponse(String question) {
        // Reformular la pregunta usando la estructura del documento
        SystemMessage structureMessage = new SystemMessage(DOCUMENT_STRUCTURE_PROMPT);
        UserMessage userMessage = new UserMessage(question);
        String reformulatedQuery = chatModel.call(structureMessage, userMessage);

        // Recuperar documentos relevantes
        SearchRequest req = SearchRequest.query(reformulatedQuery).withTopK(5);
        List<Document> relevantDocs = vectorStore.similaritySearch(req);
        String context = relevantDocs.stream()
                .map(Document::getContent)
                .reduce("", (a, b) -> a + "\n\n" + b);

        // Crear mensaje final con contexto de documentos
        Message prompt = PROMPT_TEMPLATE.createMessage(Map.of("query", reformulatedQuery, "documents", context));
        UserMessage finalUserMessage = new UserMessage(prompt.getContent());
        SystemMessage finalSystemMessage = new SystemMessage(systemPrompt);
        return chatModel.call(finalSystemMessage, finalUserMessage);
    }

}

package com.uniovi.rag.services.retriever;

import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;

import java.util.List;
import java.util.stream.Collectors;

public class DocumentFilteredContextRetriever extends FilteredContextRetriever {

    private final static String PROMPT_TEMPLATE = """
    Tu tarea es filtrar el siguiente contenido eliminando únicamente la información irrelevante en relación a la pregunta dada y a las entidades clave extraídas para esta pregunta, **sin modificar ni resumir** aquello que sí resulte útil para responderla.
    
    El contenido de las actas de reuniones de comunidad de vecinos sigue una estructura como la siguiente:
    - ACTA DE LA REUNIÓN DE LA COMUNIDAD DE VECINOS
    - Fecha (día, mes y año)
    - Lugar
    - Hora de inicio
    - Hora de fin
    - Lista de Asistentes: número de asistentes, nombres y cargos (por ejemplo: presidente, secretario).
    - Orden del Día: temas discutidos durante la reunión, entre los cuales se encuentran Acuerdos, Noticias, Decisiones Tomadas; resoluciones aprobadas o votadas.
    - Ruegos y Preguntas: intervenciones abiertas al final de la sesión.
    - Hora de finalización de la reunión.
    
    Además de la pregunta, se han identificado entidades clave relacionadas con lo que debe buscarse en el contenido de una o varias actas. 
    Estas entidades pueden ser fechas, lugares, participantes, temas o acciones importantes. Debes usarlas como ayuda para decidir qué contenido conservar.
    
    Contenido: "%s"
    Pregunta: "%s"
    Entidades clave: "%s"
    
    Devuelve únicamente el contenido filtrado (sin añadir encabezados, notas, comentarios ni explicaciones). Si no hay nada relevante, devuelve una cadena vacía ('').
    """;


    public DocumentFilteredContextRetriever(PgVectorStore vectorStore, OllamaChatModel chatModel, int topK) {
        super(vectorStore, chatModel, topK);
        setPromptTemplate(PROMPT_TEMPLATE);
    }

    @Override
    public String retrieve(String query, String context) {

        SearchRequest req = SearchRequest.query(query).withTopK(topK);
        List<Document> retrievedDocs = vectorStore.similaritySearch(req);

        return retrievedDocs.stream()
                .map(doc -> filterContentByQuestion(doc, query, context))
                .collect(Collectors.joining("\n"));
    }

    private String filterContentByQuestion(Document doc, String query, String nerContext) {

        String filterPrompt = String.format(
                promptTemplate,
                doc.getContent(), query, nerContext
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

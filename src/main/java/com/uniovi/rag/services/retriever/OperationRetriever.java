package com.uniovi.rag.services.retriever;

import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.vectorstore.PgVectorStore;

public class OperationRetriever extends LiteralRetriever {

    private static final String PROMPT_TEMPLATE = """
        Tu tarea es realizar la operación que se pide en la <Pregunta> (con los valores sacados de cada uno de los documentos en relación a lo que se pide en la <Pregunta>,
        que se encuentran en <Contenido> ya filtrados). Por ejemplo, el mínimo de los números de <Contenido>, un valor que aparece más veces en <Contenido>...
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

        <Pregunta>: %s
        <Contenido>: %s
    """;

    private static final String CONTEXT_PROMPT_TEMPLATE = """
        Tu tarea es realizar la operación que se pide en la <Pregunta> (con los valores sacados de cada una de las actas en relación a lo que se pide en la <Pregunta>,
        que se encuentran en <Contenido> ya filtrados). Por ejemplo, el mínimo de los números de <Contenido>, un valor que aparece más veces en <Contenido>...
    
        Las siguientes entidades fueron tomadas en cuenta para extraer los valores de <Contenido> de las actas: %s
        <Pregunta>: %s
        <Contenido>: %s
    """;

    public OperationRetriever(PgVectorStore vectorStore, OllamaChatModel chatModel, int topK) {
        super(vectorStore, chatModel, topK);
    }

    @Override
    public String retrieve(String query) {
        String filtered = super.retrieve(query);

        System.out.println("-------------------------------------------");
        String prompt = String.format(PROMPT_TEMPLATE, query, filtered);
        System.out.printf("OPERATION RETRIEVER prompt %s\n", prompt);

        String response = model.call(prompt);
        System.out.printf("OPERATION RETRIEVER response %s\n", response);

        return response;
    }

    @Override
    public String retrieve(String query, String context) {
        String filtered = super.retrieve(query, context);
        return model.call(String.format(CONTEXT_PROMPT_TEMPLATE, context, query, filtered));
    }
}

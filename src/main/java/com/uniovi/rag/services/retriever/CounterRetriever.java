package com.uniovi.rag.services.retriever;

import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.vectorstore.PgVectorStore;

public class CounterRetriever extends FilteredContextRetriever {

    private static final String PROMPT_TEMPLATE = """
           Tu tarea es decidir si este <Contenido> del acta crees que cuenta como un elemento más del conteo a tener en cuenta,
            en la relación con lo que se pide en la <Pregunta>. 
            Si crees que sí, devuelve SOLO "SI" con el valor del <Contenido> que creas que podría ser parte de la respuesta a la pregunta
            y si no, devuelve SOLO "NO".

           <Contenido>: %s
           <Pregunta>: %s
           """;

    private static final String NER_PROMPT_TEMPLATE = """
            Tu tarea es decidir si este <Contenido> del acta crees que cuenta como un elemento más del conteo a tener en cuenta,
            en la relación con lo que se pide en la <Pregunta>. 
            Si crees que sí, devuelve SOLO "SI" con el valor del <Contenido> que creas que podría ser parte de la respuesta a la pregunta
            y si no, devuelve SOLO "NO".
            
            Entidades que se buscan en las actas para apoyar la decisión: %s
            <Pregunta>: %s
            <Contenido>: %s
            """;

    private static final String FILTER_PROMPT_TEMPLATE = """
            Se han sacado de cada documento relevante para la <Pregunta> si el contenido estaba relacionado con la pregunta (SI o NO)
            y el contenido necesario para responder conjuntamente con el resto de respuestas si las hay a la <Pregunta>
            Tu tarea es ÚNICAMENTE responder con el conteo de los SÍ o NO en caso de que se pida una cifra numérica literal,
            si no, usa el resto de información relevante del <Contenido> sacada a su vez     de cada documento.
            
            <Contenido>: %s
            <Pregunta>: %s
            """;

    public CounterRetriever(PgVectorStore vectorStore, OllamaChatModel model, int topK) {
        super(vectorStore, model, topK);
        setPromptTemplate(PROMPT_TEMPLATE);
        setNerPromptTemplate(NER_PROMPT_TEMPLATE);
    }

    @Override
    public String retrieve(String query) {
        String filters = super.retrieve(query);

        return getResponseWithCounterFilters(query, filters);
    }

    @Override
    public String retrieve(String query, String context) {
        String filters = super.retrieve(query, context);

        return getResponseWithCounterFilters(query, filters);
    }

    private String getResponseWithCounterFilters(String query, String filters) {
        String prompt = String.format(FILTER_PROMPT_TEMPLATE, filters, query);

        System.out.println("---------------------------------");
        System.out.printf("COUNTER RETRIEVER prompt %s%n", prompt);

        String response = this.model.call(prompt);

        System.out.printf("COUNTER RETRIEVER response %s", response);

        return response;
    }

}

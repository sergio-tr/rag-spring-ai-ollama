package com.uniovi.rag.services.retriever;

import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.vectorstore.PgVectorStore;

public class    LiteralRetriever extends FilteredContextRetriever {

    private static final String PROMPT_TEMPLATE = """
        Tu tarea es sacar del <Contenido> el valor literal (número, palabra, frase) que sirva para responder a la <Pregunta>.
        Si este acta no tiene información para responder a la pregunta, responde ''
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

        NO tienes que responder a la pregunta, si no extrar el literial necesario para responderla.
        <Pregunta>: %s
        <Contenido>: %s
    """;

    private static final String CONTEXT_PROMPT_TEMPLATE = """
        Tu tarea es sacar del <Contenido> el valor literal (número, palabra, frase) que sirva para responder a la <Pregunta>.
        pero solo con aquellos trozos (o combinación de los mismos) relevantes que puedan ayudar a responder la <Pregunta>.
        Si este acta no tiene información para responder a la pregunta, responde ''
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

        NO tienes que responder a la pregunta, si no extrar el literial necesario para responderla.
        Ten en cuenta las siguientes entidades en el contenido de las actas para responder a la <Pregunta>: %s
        <Pregunta>: %s
        <Contenido>: %s
    """;

    public LiteralRetriever(PgVectorStore vectorStore, OllamaChatModel chatModel, int topK) {
        super(vectorStore, chatModel, topK);
        setPromptTemplate(PROMPT_TEMPLATE);
        setNerPromptTemplate(CONTEXT_PROMPT_TEMPLATE);
    }
}

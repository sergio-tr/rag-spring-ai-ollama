package com.uniovi.rag.services.expand;

import org.springframework.ai.chat.client.ChatClient;

public class DocumentStructureExpander extends AbstractQueryExpander {

    private static final String DOCUMENT_STRUCTURE_PROMPT = """
            Tu tarea es reformular la <Pregunta> para que sea más clara, estructurada y relevante en el contexto de actas de reuniones de comunidad de vecinos.
            
            Estas actas siguen una estructura formal con secciones como:
            - Fecha (día, mes y año)
            - Lugar
            - Hora de inicio
            - Hora de fin
            - Lista de Asistentes: número de asistentes, nombres y cargos (por ejemplo: presidente, secretario).
            - Orden del Día: temas discutidos durante la reunión, entre los cuales se encuentran Acuerdos, Noticias, Decisiones Tomadas; resoluciones aprobadas o votadas.
            - Ruegos y Preguntas: intervenciones abiertas al final de la sesión.
            
            Reformula la pregunta teniendo en cuenta esta estructura, usando términos específicos de las secciones que se usan en el texto anterior sobre la estructura de las actas
            pero usando también las palabras del usuario y orientando la consulta que generes para facilitar la localización exacta de la información dentro de las actas posteriormente.
            
            No respondas a la pregunta. Devuelve únicamente UNA ÚNICA NUEVA PREGUNTA O FRASE que pida exactamente lo mismo, pero con los términos y secciones utilizados previamente para identificar las secciones de las actas.
            Si no se puede reformular la pregunta porque ya esté bien reformulada, únicamente devuelve la pregunta original.
            
            <Pregunta> original: "%s"
            Pregunta reformulada:
            """;

    public DocumentStructureExpander(ChatClient client) {
        super(client);
    }

    @Override
    public String expand(String query) {
        String prompt = String.format(DOCUMENT_STRUCTURE_PROMPT, query);

        String result = client.prompt()
                .user(prompt)
                .call()
                .content()
                .trim();

        log().info("-----------------------------------------------------------------------------");
        log().info("EXPANDER: Pregunta original: {}", query);
        log().info("EXPANDER: Pregunta reformulada: {}", result);

        return result;
    }
}

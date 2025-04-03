package com.uniovi.rag.services.query;

import com.uniovi.rag.services.analyzer.QueryAnalyser;
import com.uniovi.rag.services.expand.QueryExpander;
import com.uniovi.rag.services.retriever.ContextRetriever;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.stereotype.Service;

@Service
public class SimpleQueryService extends AbstractQueryService{

    private String context;

    public SimpleQueryService(QueryExpander expander, QueryAnalyser analyser, ContextRetriever retriever, OllamaChatModel chatModel) {
        super(expander, analyser, retriever, chatModel);
    }

    public String generateResponse(String question) {


        String templateContext =
                "ACTA DE LA REUNIÓN DE LA COMUNIDAD DE VECINOS \n" +
                "Fecha: 25 de agosto de 2025 \n" +
                "Lugar: Sala de reuniones del edificio \n" +
                "Hora de inicio: 19:30 h \n" +
                "Hora de finalización: 21:00 h \n" +
                "Asistentes: \n" +
                "Se cuenta con la asistencia de 18 propietarios, según la lista de asistencia firmada al inicio de la reunión. \n" +
                "• Beatriz Suárez Aguilar (Presidente) \n" +
                "• Manuel Ortega Medina \n" +
                "• Isabel Castro Torres \n" +
                "• Jorge Moreno Navarro \n" +
                "• Patricia Navarro Díaz \n" +
                "• Eduardo Rojas Martínez \n" +
                "• Silvia Medina Pérez \n" +
                "• Ricardo Flores Sánchez \n" +
                "• Daniel Gutiérrez Moreno \n" +
                "• Rosa Aguilar Fernández \n" +
                "• Laura Díaz Castro \n" +
                "• Marta González Ramírez \n" +
                "• Antonio Martínez López \n" +
                "• Alejandro Torres Rojas \n" +
                "• Natalia Vázquez Gutiérrez (Secretaria) \n" +
                "• Francisco Torres Delgado \n" +
                "• Pedro Jiménez Suárez \n" +
                "• Ana Sánchez Herrera \n" +
                "\n" +
                "ACTA DE LA REUNIÓN DE LA COMUNIDAD DE VECINOS \n" +
                "Fecha: 25 de febrero de 2026 \n" +
                "Lugar: Sala de reuniones del edificio \n" +
                "Hora de inicio: 19:00 h \n" +
                "Hora de finalización: 20:30 h \n" +
                "Asistentes: \n" +
                "Se cuenta con la asistencia de 17 propietarios, según la lista de asistencia firmada al inicio de la reunión. \n" +
                "• Jorge Moreno Navarro (Presidente) \n" +
                "• Laura Díaz Castro \n" +
                "• Manuel Ortega Medina \n" +
                "• Rosa Aguilar Fernández \n" +
                "• Ricardo Flores Sánchez \n" +
                "• Beatriz Suárez Aguilar \n" +
                "• Pedro Jiménez Suárez \n" +
                "• Ana Sánchez Herrera \n" +
                "• Patricia Navarro Díaz \n" +
                "• Eduardo Rojas Martínez \n" +
                "• Silvia Medina Pérez \n" +
                "• Francisco Torres Delgado \n" +
                "• Daniel Gutiérrez Moreno \n" +
                "• Natalia Vázquez Gutiérrez (Secretaria) \n" +
                "• Antonio Martínez López \n" +
                "• Isabel Castro Torres \n" +
                "• Marta González Ramírez \n";
        context = templateContext;

        //  System.out.println("Contexto original --------------------------------------------------------------------------------\n " + context);

        String promtPruebaFiltro = "<pregunta>Repite exactamente el texto que te adjunto pero eliminando todos los nombres propios que NO sean Manuel Ortega Medina </pregunta>" +
                "<texto>"+context+"</texto> " +
                "<pregunta>Repite exactamente el texto que te adjunto pero eliminando todos los nombres propios que NO sean Manuel Ortega Medina </pregunta>";

        //context = chatModel.call(promtPruebaFiltro);
        context = askQueryToLlama(promtPruebaFiltro);

        System.out.println("Contexto Filtrado --------------------------------------------------------------------------------\n " + context);

        System.out.println(" --------------------------------------------------------------------------------\n ");
        // No es capaz de retornar todas las actas.
        /**
        Message prompt = promptTemplate.createMessage(Map.of("query", question, "documents", context));
        UserMessage userMessage = new UserMessage(prompt.getContent());
        //System.out.println("Question: " + prompt.getContent());
        SystemMessage systemMessage = new SystemMessage(systemPrompt);
        //System.out.println("System prompt: " + systemPrompt);
        **/



        String template = "Responde siempre en español a la pregunta <pregunta>" + question +
                "</pregunta> usando únicamente esta información del contexto <contexto>\n\n" +
                context + "</contexto> Responde siempre en español a la pregunta <pregunta>" +
                question + "</pregunta> usando únicamente esta información del contexto.";

        System.out.println("template: " + template);
        System.out.println("tokens:"+countTokens(template));

        return chatModel.call(template);
    }

}
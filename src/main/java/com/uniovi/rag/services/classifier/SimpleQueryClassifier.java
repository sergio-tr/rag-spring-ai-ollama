package com.uniovi.rag.services.classifier;

import org.springframework.ai.ollama.OllamaChatModel;

public class SimpleQueryClassifier implements QueryClassifier {

    private final OllamaChatModel model;

    private static final String PROMPT_TEMPLATE = """
        Eres un clasificador de preguntas. Dado el texto de una consulta, tu tarea es devolver únicamente el tipo de clasificación correspondiente según las siguientes categorías (escribe solo el nombre en mayúsculas):
        
        - COUNTER: cuando la consulta busca contar cuántas veces ocurre algo, cuántos documentos cumplen una condición o cuántas personas, fechas o temas aparecen. La respuesta es un número.
        - LITERAL: cuando se busca una frase, palabra o número que aparece de forma textual en un documento.
        - OPERATION: cuando se necesita revisar todos los documentos y combinar o resumir algo de cada uno, típicamente con razonamiento o agregación.
        - PARAGRAPH: cuando se desea devolver el contenido de párrafos concretos donde se menciona algo.
        - CONTENT: cualquier otra consulta general o ambigua.
        
        Clasifica las siguientes consultas:
        
        Consulta: ¿A cuántas reuniones asistió Juan Pérez?
        Tipo: COUNTER
        
        Consulta: ¿Qué se dijo sobre el sistema de calefacción?
        Tipo: PARAGRAPH
        
        Consulta: ¿Cuántos documentos mencionan la palabra ascensor?
        Tipo: COUNTER
        
        Consulta: ¿Quién presidía la reunión del 25 de febrero de 2025?
        Tipo: LITERAL
        
        Consulta: ¿Qué decisiones se tomaron en cada acta sobre el tema de seguridad?
        Tipo: OPERATION
        
        Consulta: %s
        Tipo:
        """;

    public SimpleQueryClassifier(OllamaChatModel model) {
        this.model = model;
    }

    @Override
    public QueryType classify(String query){
        String prompt = String.format(PROMPT_TEMPLATE, query);
        String response = model.call(prompt);

        return parseResponse(response);
    }

    private QueryType parseResponse(String response) {
        String cleaned = response.strip().toUpperCase();

        for (QueryType type : QueryType.values()) {
            if (cleaned.contains(type.name())) {
                return type;
            }
        }

        return QueryType.CONTENT;
    }
}

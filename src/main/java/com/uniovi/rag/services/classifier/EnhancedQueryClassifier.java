package com.uniovi.rag.services.classifier;

import org.springframework.ai.chat.client.ChatClient;

public class EnhancedQueryClassifier implements QueryClassifier {

    private final QueryClassifier baseClassifier;
    private final ChatClient chatClient;

    public EnhancedQueryClassifier(QueryClassifier baseClassifier, ChatClient chatClient) {
        this.baseClassifier = baseClassifier;
        this.chatClient = chatClient;
    }

    @Override
    public String classifyWithText(String query) {
        return getRefinedType(query);
    }

    @Override
    public QueryType classify(String query) {
        try {
            return QueryType.valueOf(classifyWithText(query));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String getRefinedType(String query) {
        String initialType = baseClassifier.classifyWithText(query);
        String refinedType = validateWithLLM(query, initialType);

        System.out.println("[CLASSIFIER] Initial type: " + initialType);
        System.out.println("[CLASSIFIER] Refined type: " + refinedType);

        return refinedType;
    }

    private String validateWithLLM(String query, String initialType) {
        String prompt = """
                Eres un sistema experto en clasificación de preguntas realizadas sobre actas de reuniones. A continuación tienes los tipos de pregunta posibles en este sistema, junto con sus definiciones:
                
                Tipos posibles de pregunta:
                1. COUNT_DOCUMENTS → ¿Cuántos documentos cumplen una condición?
                2. COUNT_AND_EXPLAIN → ¿Cuántos documentos tratan un tema y qué se dijo en ellos?
                3. EXTRACT_ENTITIES → Extraer personas, entidades, cargos, asistentes...
                4. FIND_PARAGRAPH → Localizar fragmentos literales sobre un tema.
                5. GET_FIELD → Obtener un valor literal directamente (fecha, lugar, presidente…).
                6. BOOLEAN_QUERY → Confirmar si algo ocurrió (se mencionó, se aprobó…).
                7. COMPARE → Comparar entre valores de diferenes actas (asistentes, duración, menciones…).
                8. SUMMARIZE_TOPIC → Resumir lo dicho sobre un tema concreto.
                9. SUMMARIZE_MEETING → Resumir una reunión completa.
                10. DECISION_EXTRACTION → Extraer las decisiones acordadas.
                11. FILTER_AND_LIST → Aplicar múltiples filtros y listar resultados.
                12. GET_DURATION → Obtener la duración de una reunión.
                
                Tu tarea es validar si la clasificación inicial es correcta o debe corregirse.
                
                Pregunta:
                "%s"
                
                Clasificación propuesta:
                %s
                
                Devuelve únicamente uno de los siguientes identificadores válidos (sin explicaciones ni comillas):
                COUNT_DOCUMENTS, COUNT_AND_EXPLAIN, EXTRACT_ENTITIES, FIND_PARAGRAPH, GET_FIELD,
                BOOLEAN_QUERY, COMPARE, SUMMARIZE_TOPIC, SUMMARIZE_MEETING,
                DECISION_EXTRACTION, FILTER_AND_LIST, GET_DURATION.
                """.formatted(query, initialType);

        return chatClient
                .prompt()
                .user(prompt)
                .call()
                .content()
                .strip()
                .toUpperCase();
    }
}

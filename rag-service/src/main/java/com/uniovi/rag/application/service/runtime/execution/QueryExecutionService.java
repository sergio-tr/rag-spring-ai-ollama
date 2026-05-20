package com.uniovi.rag.application.service.runtime.execution;

import com.uniovi.rag.infrastructure.observability.Loggable;
import com.uniovi.rag.application.result.chat.QueryResponse;

public interface QueryExecutionService extends Loggable {

    /**
     * @param chatModel nombre del modelo Ollama para esta respuesta (lab); {@code null} usa el configurado por defecto
     */
    QueryResponse generateResponse(String question, String chatModel);

    default QueryResponse generateResponse(String question) {
        return generateResponse(question, null);
    }
}

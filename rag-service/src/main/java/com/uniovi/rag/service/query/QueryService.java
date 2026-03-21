package com.uniovi.rag.service.query;

import com.uniovi.rag.model.Loggable;
import com.uniovi.rag.model.QueryResponse;

public interface QueryService extends Loggable {

    /**
     * @param chatModel nombre del modelo Ollama para esta respuesta (lab); {@code null} usa el configurado por defecto
     */
    QueryResponse generateResponse(String question, String chatModel);

    default QueryResponse generateResponse(String question) {
        return generateResponse(question, null);
    }
}

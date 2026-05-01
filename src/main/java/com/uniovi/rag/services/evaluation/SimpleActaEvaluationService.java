package com.uniovi.rag.services.evaluation;

import com.uniovi.rag.services.DocumentService;
import com.uniovi.rag.services.query.QueryService;
import com.uniovi.rag.services.query.SimpleQueryService;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SimpleActaEvaluationService extends ActaEvaluationService {

    public SimpleActaEvaluationService(OllamaChatModel chatModel, DocumentService documentService, QueryService queryService) {
        super(chatModel, documentService, queryService);
    }

    @Override
    public List<String> getSystemPrompts() {
        return List.of("""
                Responde siempre en español. A continuación, tienes información clave para interpretar las preguntas y generar respuestas correctas.
                Presta especial atención a los elementos de contexto para asegurar precisión en las respuestas.
                """);
    }
}

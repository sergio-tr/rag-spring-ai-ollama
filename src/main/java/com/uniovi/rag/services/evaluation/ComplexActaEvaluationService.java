package com.uniovi.rag.services.evaluation;

import com.uniovi.rag.services.DocumentService;
import com.uniovi.rag.services.query.SimpleQueryService;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.stereotype.Service;

@Service
public class ComplexActaEvaluationService extends SimpleActaEvaluationService{

    public ComplexActaEvaluationService(OllamaChatModel chatModel, DocumentService documentService, SimpleQueryService queryService) {
        super(chatModel, documentService, queryService);
    }

}

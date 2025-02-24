package com.uniovi.rag.configuration;

import com.uniovi.rag.services.*;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RagConfiguration {

    @Bean
    public OllamaEmbeddingModel embeddingModel(@Value("${spring.ai.ollama.base-url}") String url, @Value("${spring.ai.ollama.embedding.model}") String model) {
        return new OllamaEmbeddingModel(new OllamaApi(url), OllamaOptions.create().withModel(model));
    }

    @Bean
    public OllamaChatModel chatModel(@Value("${spring.ai.ollama.base-url}") String url, @Value("${spring.ai.ollama.chat.model}") String model) {
        return new OllamaChatModel(new OllamaApi(url), OllamaOptions.create().withModel(model));
    }

//    @Bean
//    public EvaluationService evaluationService(OllamaChatModel chatModel, DocumentService documentService, QueryService queryService) {
//        return new ExcelEvaluationService(chatModel, documentService, queryService);
//    }

    @Bean
    public EvaluationService evaluationService(OllamaChatModel chatModel, DocumentService documentService, QueryService queryService) {
        return new PdfEvaluationService(chatModel, documentService, queryService);
    }

}
package com.uniovi.rag.configuration;

import com.uniovi.rag.services.*;
import com.uniovi.rag.services.analyzer.NERQueryAnalyser;
import com.uniovi.rag.services.analyzer.QueryAnalyser;
import com.uniovi.rag.services.classifier.SimpleQueryClassifier;
import com.uniovi.rag.services.classifier.QueryType;
import com.uniovi.rag.services.evaluation.EvaluationService;
import com.uniovi.rag.services.evaluation.SimpleActaEvaluationService;
import com.uniovi.rag.services.expand.DocumentStructureExpander;
import com.uniovi.rag.services.expand.QueryExpander;
import com.uniovi.rag.services.query.QueryService;
import com.uniovi.rag.services.query.SimpleProcessQueryService;
import com.uniovi.rag.services.retriever.*;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

@Configuration
public class RagConfiguration {

    @Value("${spring.ai.ollama.top-k}")
    private int topK;

    @Bean
    public PgVectorStore pgVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        return new PgVectorStore(jdbcTemplate, embeddingModel);
    }

    @Bean
    public OllamaEmbeddingModel embeddingModel(@Value("${spring.ai.ollama.base-url}") String url, @Value("${spring.ai.ollama.embedding.model}") String model) {
        return new OllamaEmbeddingModel(new OllamaApi(url), OllamaOptions.create().withModel(model));
    }

    @Bean
    public OllamaChatModel chatModel(@Value("${spring.ai.ollama.base-url}") String url, @Value("${spring.ai.ollama.chat.model}") String model) {
        return new OllamaChatModel(new OllamaApi(url), OllamaOptions.create().withModel(model));
    }

    @Bean
    public EvaluationService evaluationService(OllamaChatModel chatModel, DocumentService documentService, QueryService queryService) {
        return new SimpleActaEvaluationService(chatModel, documentService, queryService);
    }

    @Bean
    public QueryExpander queryExpander(OllamaChatModel chatModel) {
        return new DocumentStructureExpander(chatModel);
    }

    @Bean
    SimpleQueryClassifier queryClassifier(OllamaChatModel model){
        return new SimpleQueryClassifier(model);
    }

    @Bean
    public QueryAnalyser queryAnalyser(OllamaChatModel chatModel) {
        return new NERQueryAnalyser(chatModel);
    }

    @Bean
    public ContextRetriever contextRetriever(PgVectorStore vectorStore, OllamaChatModel chatModel) {
        return new DocumentFilteredContextRetriever(vectorStore, chatModel, topK);
    }

//    @Bean
//    public QueryService queryService(QueryExpander expander, QueryAnalyser analyser, ContextRetriever retriever, OllamaChatModel chatModel) {
//        return new ComplexQueryService(expander, analyser, retriever, chatModel);
//    }

    @Bean
    public QueryService queryService(QueryExpander expander, SimpleQueryClassifier classifier, QueryAnalyser analyser,
                                     PgVectorStore vectorStore, OllamaChatModel chatModel) {
        return new SimpleProcessQueryService(expander, classifier, analyser, chatModel, retrieversByType(vectorStore, chatModel));
    }

    @Bean
    public Map<QueryType, ContextRetriever> retrieversByType(PgVectorStore vectorStore, OllamaChatModel model) {
        return  Map.of(
            QueryType.COUNTER, new CounterRetriever(vectorStore, model, topK),
            QueryType.LITERAL, new LiteralRetriever(vectorStore, model, topK),
            QueryType.PARAGRAPH, new ParagraphRetriever(vectorStore, model, topK),
            QueryType.OPERATION, new OperationRetriever(vectorStore, model, topK),
            QueryType.CONTENT, new ContentRetriever(vectorStore, model, topK)
        );
    }



}
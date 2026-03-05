package com.uniovi.rag.configuration;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import com.uniovi.rag.service.extraction.DefaultDocumentContentExtractor;
import com.uniovi.rag.service.extraction.DocumentContentExtractor;
import com.uniovi.rag.service.postretrieval.DefaultPostRetrievalProcessor;
import com.uniovi.rag.service.postretrieval.PostRetrievalProcessor;
import com.uniovi.rag.service.retriever.BasicContextRetriever;
import com.uniovi.rag.service.retriever.ContextRetriever;
import com.uniovi.rag.service.retriever.FilteredContextRetriever;
import com.uniovi.rag.service.retriever.MinuteDocumentContextRetriever;

import org.springframework.ai.vectorstore.pgvector.PgVectorStore;

@Configuration
public class RagRetrievalConfiguration {

    @Bean
    public PgVectorStore pgVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel).build();
    }

    @Bean
    public PostRetrievalProcessor postRetrievalProcessor(@Value("${rag.post-retrieval.top-k:10}") int topK) {
        return new DefaultPostRetrievalProcessor(topK);
    }

    @Bean
    public DocumentContentExtractor documentContentExtractor() {
        return new DefaultDocumentContentExtractor();
    }

    @Bean
    public ContextRetriever retriever(
        PgVectorStore vectorStore,
        ChatClient chatClient,
        RagImplementationProperties implProps,
        @Value("${spring.ai.ollama.top-k}") int topK,
        @Value("${spring.ai.ollama.similarity-threshold}") double similarityThreshold
    ) {
        String impl = implProps.getRetrieverImpl() != null ? implProps.getRetrieverImpl().trim().toLowerCase() : "basic";
        return switch (impl) {
            case "filtered" -> new FilteredContextRetriever(vectorStore, chatClient, topK, similarityThreshold);
            case "minute-document" -> new MinuteDocumentContextRetriever(vectorStore, chatClient, topK, similarityThreshold);
            default -> new BasicContextRetriever(vectorStore, chatClient, topK, similarityThreshold);
        };
    }
}

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
import com.uniovi.rag.infrastructure.observability.ObservabilitySupport;
import com.uniovi.rag.infrastructure.observability.TracedContextRetriever;
import com.uniovi.rag.infrastructure.observability.TracedDocumentContentExtractor;
import com.uniovi.rag.infrastructure.observability.TracedPostRetrievalProcessor;
import com.uniovi.rag.service.retriever.BasicContextRetriever;
import com.uniovi.rag.service.retriever.ContextRetriever;
import com.uniovi.rag.service.retriever.FilteredContextRetriever;
import com.uniovi.rag.service.retriever.MinuteDocumentContextRetriever;

import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Autowired;

@Configuration
public class RagRetrievalConfiguration {

    @Bean
    public PgVectorStore pgVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel).build();
    }

    @Bean
    public PostRetrievalProcessor postRetrievalProcessor(
            @Value("${rag.post-retrieval.top-k:10}") int topK,
            @Autowired(required = false) ObservabilitySupport observability
    ) {
        PostRetrievalProcessor raw = new DefaultPostRetrievalProcessor(topK);
        if (observability != null) {
            return new TracedPostRetrievalProcessor(raw, observability);
        }
        return raw;
    }

    @Bean
    public DocumentContentExtractor documentContentExtractor(
            @Autowired(required = false) ObservabilitySupport observability
    ) {
        DocumentContentExtractor raw = new DefaultDocumentContentExtractor();
        if (observability != null) {
            return new TracedDocumentContentExtractor(raw, observability);
        }
        return raw;
    }

    @Bean
    public ContextRetriever retriever(
        PgVectorStore vectorStore,
        ChatClient chatClient,
        RagImplementationProperties implProps,
        @Value("${spring.ai.ollama.top-k:80}") int topK,
        @Value("${spring.ai.ollama.similarity-threshold:0.25}") double similarityThreshold,
        @Value("${knowledge.v2.chat-overlay.enabled:false}") boolean knowledgeChatOverlayEnabled,
        @Autowired(required = false) ObservabilitySupport observability
    ) {
        String impl = implProps.getRetrieverImpl() != null ? implProps.getRetrieverImpl().trim().toLowerCase() : "basic";
        ContextRetriever retriever;
        switch (impl) {
            case "filtered":
                retriever = new FilteredContextRetriever(vectorStore, chatClient, topK, similarityThreshold, knowledgeChatOverlayEnabled);
                break;
            case "minute-document":
                retriever = new MinuteDocumentContextRetriever(vectorStore, chatClient, topK, similarityThreshold, knowledgeChatOverlayEnabled);
                break;
            default:
                retriever = new BasicContextRetriever(vectorStore, chatClient, topK, similarityThreshold, knowledgeChatOverlayEnabled);
        }
        if (observability != null) {
            return new TracedContextRetriever(retriever, observability);
        }
        return retriever;
    }
}

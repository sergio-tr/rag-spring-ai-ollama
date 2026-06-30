package com.uniovi.rag.configuration;

import org.springframework.ai.chat.client.ChatClient;
import com.uniovi.rag.infrastructure.llm.LlmProperties;
import com.uniovi.rag.infrastructure.vector.ProviderAwareEmbeddingModelFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import com.uniovi.rag.application.service.runtime.document.extraction.DefaultDocumentContentExtractor;
import com.uniovi.rag.application.service.runtime.document.extraction.DocumentContentExtractor;
import com.uniovi.rag.application.service.runtime.retrieval.post.DefaultPostRetrievalProcessor;
import com.uniovi.rag.application.service.runtime.retrieval.post.PostRetrievalProcessor;
import com.uniovi.rag.infrastructure.observability.ObservabilitySupport;
import com.uniovi.rag.infrastructure.observability.TracedContextRetriever;
import com.uniovi.rag.infrastructure.observability.TracedDocumentContentExtractor;
import com.uniovi.rag.infrastructure.observability.TracedPostRetrievalProcessor;
import com.uniovi.rag.application.service.knowledge.EmbeddingIndexCompatibilityService;
import com.uniovi.rag.application.service.runtime.retrieval.BasicContextRetriever;
import com.uniovi.rag.application.service.runtime.retrieval.ContextRetriever;
import com.uniovi.rag.application.service.runtime.retrieval.FilteredContextRetriever;
import com.uniovi.rag.application.service.runtime.retrieval.MinuteDocumentContextRetriever;
import com.uniovi.rag.application.service.runtime.retrieval.ProviderAwareContextRetriever;

import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Autowired;

@Configuration
public class RagRetrievalConfiguration {

    @Bean
    public PgVectorStore pgVectorStore(
            JdbcTemplate jdbcTemplate,
            ProviderAwareEmbeddingModelFactory embeddingModelFactory,
            LlmProperties llmProperties) {
        String modelId = llmProperties.effectiveDefaultEmbeddingModel();
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalStateException(
                    "Effective default embedding model is not configured; set rag.llm.ollama.default-embedding-model "
                            + "or rag.llm.openai-compatible.default-embedding-model for the active provider.");
        }
        return PgVectorStore.builder(jdbcTemplate, embeddingModelFactory.forModel(modelId)).build();
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
        EmbeddingIndexCompatibilityService embeddingIndexCompatibilityService,
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
        retriever = new ProviderAwareContextRetriever(retriever, embeddingIndexCompatibilityService);
        if (observability != null) {
            return new TracedContextRetriever(retriever, observability);
        }
        return retriever;
    }
}

package com.uniovi.rag.configuration;

import com.uniovi.rag.observability.ObservabilitySupport;
import com.uniovi.rag.observability.TracedDocumentService;
import com.uniovi.rag.observability.TracedMinuteDocumentRepository;
import com.uniovi.rag.repository.MinuteDocumentRepository;
import com.uniovi.rag.repository.impl.MinuteDocumentRepositoryImpl;
import com.uniovi.rag.service.document.DocumentService;
import com.uniovi.rag.service.document.MetadataMinuteDocumentService;
import com.uniovi.rag.service.document.SimpleDocumentService;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class RagDocumentConfiguration {

    @Bean
    public DocumentService documentService(
        RagFeatureConfiguration featureConfig,
        PgVectorStore vectorStore,
        ChatClient chatClient,
        JdbcTemplate jdbcTemplate,
        MetadataMinuteDocumentService metadataMinuteDocumentService,
        SimpleDocumentService<?> simpleDocumentService,
        @org.springframework.beans.factory.annotation.Autowired(required = false) ObservabilitySupport observability
    ) {
        DocumentService raw = featureConfig.isMetadataEnabled()
                ? metadataMinuteDocumentService
                : simpleDocumentService;
        if (observability != null) {
            return new TracedDocumentService(raw, observability);
        }
        return raw;
    }

    @Bean
    public MinuteDocumentRepository minuteDocumentRepository(
            DocumentService documentService,
            MetadataMinuteDocumentService metadataMinuteDocumentService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) ObservabilitySupport observability) {
        MinuteDocumentRepository raw = new MinuteDocumentRepositoryImpl(documentService, metadataMinuteDocumentService);
        if (observability != null) {
            return new TracedMinuteDocumentRepository(raw, observability);
        }
        return raw;
    }
}

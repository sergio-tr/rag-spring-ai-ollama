package com.uniovi.rag.configuration;

import com.uniovi.rag.application.port.ClassifierLabPort;
import com.uniovi.rag.application.port.ClassifierTrainBytesCommand;
import com.uniovi.rag.application.service.knowledge.EmbeddingIndexCompatibilityService;
import com.uniovi.rag.application.service.knowledge.IndexProfileJsonSupport;
import com.uniovi.rag.application.service.llm.ProviderAwareEmbeddingService;
import com.uniovi.rag.configuration.RagVectorProperties;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.infrastructure.vector.EmbeddingSpaceGuard;
import com.uniovi.rag.infrastructure.vector.OllamaEmbeddingModelFactory;
import com.uniovi.rag.infrastructure.vector.ProviderAwareEmbeddingModelFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagE2eAiStubConfigurationTest {

    @Test
    void stubEmbeddingAndChatModelsAreDeterministic() {
        RagE2eAiStubConfiguration cfg = new RagE2eAiStubConfiguration();
        EmbeddingModel emb = cfg.e2eEmbeddingModel();
        assertEquals(RagE2eAiStubConfiguration.E2E_EMBEDDING_DIMENSIONS, emb.dimensions());
        ChatModel chat = cfg.e2eChatModel();
        String text = chat.call(new Prompt("ping")).getResult().getOutput().getText();
        assertTrue(text.contains("E2E stub"));
    }

    @Test
    void e2ePerModelEmbeddingFactoryDoesNotCallOllama() {
        RagE2eAiStubConfiguration cfg = new RagE2eAiStubConfiguration();
        OllamaEmbeddingModelFactory factory = cfg.e2eOllamaEmbeddingModelFactory();

        EmbeddingModel model = factory.forModel("mxbai-embed-large");

        assertEquals(RagE2eAiStubConfiguration.E2E_EMBEDDING_DIMENSIONS, model.dimensions());
        assertEquals(RagE2eAiStubConfiguration.E2E_EMBEDDING_DIMENSIONS, model.embed("probe").length);
    }

    @Test
    void e2eProviderAwareEmbeddingServiceSupportsKnowledgeIngestCompatibility() {
        RagE2eAiStubConfiguration cfg = new RagE2eAiStubConfiguration();
        ProviderAwareEmbeddingService embeddingService =
                cfg.e2eProviderAwareEmbeddingService(null, null);

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put(IndexProfileJsonSupport.EMBEDDING_MODEL_ID_KEY, RagE2eAiStubConfiguration.E2E_EMBEDDING_MODEL_ID);
        profile.put(IndexProfileJsonSupport.EMBEDDING_PROVIDER_KEY, LlmProvider.OLLAMA_NATIVE.name());

        EmbeddingIndexCompatibilityService compatibility =
                new EmbeddingIndexCompatibilityService(embeddingService, null, null);
        Map<String, Object> enriched = compatibility.enrichIndexProfile(profile);

        assertEquals(LlmProvider.OLLAMA_NATIVE.name(), enriched.get(IndexProfileJsonSupport.EMBEDDING_PROVIDER_KEY));
        assertEquals(RagE2eAiStubConfiguration.E2E_EMBEDDING_MODEL_ID, enriched.get(IndexProfileJsonSupport.EMBEDDING_MODEL_ID_KEY));
        compatibility.assertIndexingCompatible(enriched);

        var response = embeddingService.embed(RagE2eAiStubConfiguration.E2E_EMBEDDING_MODEL_ID, List.of("chunk"));
        assertEquals(RagE2eAiStubConfiguration.E2E_EMBEDDING_DIMENSIONS, response.embeddings().getFirst().length);
    }

    @Test
    void e2eProviderAwareEmbeddingModelFactorySupportsDimensionProbe() {
        RagE2eAiStubConfiguration cfg = new RagE2eAiStubConfiguration();
        ProviderAwareEmbeddingModelFactory factory = cfg.e2eProviderAwareEmbeddingModelFactory();
        EmbeddingSpaceGuard guard = new EmbeddingSpaceGuard(factory, new RagVectorProperties(1024, true));

        assertEquals(RagE2eAiStubConfiguration.E2E_EMBEDDING_DIMENSIONS,
                guard.assertFitsPhysicalVectorColumnReturning(RagE2eAiStubConfiguration.E2E_EMBEDDING_MODEL_ID));
    }

    @Test
    void e2eClassifierLabPortIsConfiguredAndDeterministic() {
        RagE2eAiStubConfiguration cfg = new RagE2eAiStubConfiguration();
        ClassifierLabPort classifier = cfg.e2eClassifierLabPort();

        assertTrue(classifier.isConfigured());
        var train = classifier.trainBytes(new ClassifierTrainBytesCommand(
                "Question,QueryType\nHow many?,COUNT_DOCUMENTS\n".getBytes(),
                "classifier.xlsx",
                "demo-model",
                null,
                null,
                null,
                1,
                1));

        assertEquals("e2e/demo-model", train.get("modelId"));
        assertNotNull(train.get("metrics"));
        assertEquals("COUNT_DOCUMENTS", classifier.classify("How many?", "e2e/demo-model").get("queryType"));
    }
}

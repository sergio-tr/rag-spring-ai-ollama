package com.uniovi.rag.configuration;

import com.uniovi.rag.application.port.ClassifierLabPort;
import com.uniovi.rag.application.port.ClassifierTrainBytesCommand;
import com.uniovi.rag.infrastructure.vector.OllamaEmbeddingModelFactory;
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

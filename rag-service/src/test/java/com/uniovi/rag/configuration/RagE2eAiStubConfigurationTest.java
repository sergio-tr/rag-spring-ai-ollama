package com.uniovi.rag.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}

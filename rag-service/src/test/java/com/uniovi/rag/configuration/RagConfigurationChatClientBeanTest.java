package com.uniovi.rag.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class RagConfigurationChatClientBeanTest {

    @Test
    void chatClientBeanBuildsFromChatModel() {
        ChatModel model = mock(ChatModel.class);
        ChatClient client = new RagConfiguration().chatClient(model);
        assertNotNull(client);
    }
}

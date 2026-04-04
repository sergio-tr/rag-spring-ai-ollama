package com.uniovi.rag.service.expand;

import com.uniovi.rag.domain.model.ExpansionStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Covers {@link AbstractQueryExpander} via concrete subclass. */
class AbstractQueryExpanderTest {

    @Test
    void minuteDocumentStructureExpander_isInstanceOfAbstractQueryExpander() {
        ChatClient chatClient = mock(ChatClient.class);
        MinuteDocumentStructureExpander expander = new MinuteDocumentStructureExpander(
                chatClient, ExpansionStrategy.COT, 1, 350, 512, 500, 200);
        assertNotNull(expander);
        assertTrue(expander instanceof AbstractQueryExpander);
    }
}

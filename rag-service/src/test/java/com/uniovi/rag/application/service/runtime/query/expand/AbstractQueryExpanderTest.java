package com.uniovi.rag.application.service.runtime.query.expand;

import com.uniovi.rag.application.service.llm.ProviderAwareSecondaryLlmExecutor;
import com.uniovi.rag.domain.model.ExpansionStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** Covers {@link AbstractQueryExpander} via concrete subclass. */
class AbstractQueryExpanderTest {

    @Test
    void minuteDocumentStructureExpander_isInstanceOfAbstractQueryExpander() {
        ProviderAwareSecondaryLlmExecutor secondaryLlmExecutor = mock(ProviderAwareSecondaryLlmExecutor.class);
        MinuteDocumentStructureExpander expander =
                new MinuteDocumentStructureExpander(secondaryLlmExecutor, ExpansionStrategy.COT, 1, 350, 512, 500, 200);
        assertNotNull(expander);
        assertTrue(expander instanceof AbstractQueryExpander);
    }
}

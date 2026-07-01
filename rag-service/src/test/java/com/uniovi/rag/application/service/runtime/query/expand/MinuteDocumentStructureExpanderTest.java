package com.uniovi.rag.application.service.runtime.query.expand;

import com.uniovi.rag.application.service.llm.ProviderAwareSecondaryLlmExecutor;
import com.uniovi.rag.domain.model.ExpansionStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MinuteDocumentStructureExpanderTest {

    private ProviderAwareSecondaryLlmExecutor secondaryLlmExecutor;
    private MinuteDocumentStructureExpander expander;

    @BeforeEach
    void setUp() {
        secondaryLlmExecutor = mock(ProviderAwareSecondaryLlmExecutor.class);
        expander = new MinuteDocumentStructureExpander(secondaryLlmExecutor);
    }

    @Test
    void expand_nullReturnsEmpty() {
        assertEquals("", expander.expand(null));
    }

    @Test
    void expand_blankReturnsAsIs() {
        assertEquals("", expander.expand(""));
        assertEquals("   ", expander.expand("   "));
    }

    @Test
    void expand_withMockedLlmReturnsOriginalPlusExpansion() {
        when(secondaryLlmExecutor.complete(
                        eq(MinuteDocumentStructureExpander.OPERATION_QUERY_EXPANSION), isNull(), anyString()))
                .thenReturn("reunión acta fecha asistentes");

        String result = expander.expand("¿Quién presidió?");

        assertNotNull(result);
        assertTrue(result.contains("¿Quién presidió?"));
        verify(secondaryLlmExecutor)
                .complete(
                        eq(MinuteDocumentStructureExpander.OPERATION_QUERY_EXPANSION),
                        isNull(),
                        anyString());
    }
}

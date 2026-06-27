package com.uniovi.rag.infrastructure.llm.openaicompat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class OpenAiCompatibleApiKeyResolverTest {

    @Test
    void resolveReturnsTrimmedValue() {
        OpenAiCompatibleApiKeyResolver resolver = new OpenAiCompatibleApiKeyResolver(name -> "  sk-test  ");
        assertEquals("sk-test", resolver.resolve("OPENAI_COMPAT_TEST_KEY"));
    }

    @Test
    void resolveRejectsMissingVariable() {
        OpenAiCompatibleApiKeyResolver resolver = new OpenAiCompatibleApiKeyResolver(name -> null);
        OpenAiCompatibleLlmException ex =
                assertThrows(OpenAiCompatibleLlmException.class, () -> resolver.resolve("MISSING_ENV_VAR"));
        assertEquals(OpenAiCompatibleLlmFailureKind.MISCONFIGURED, ex.kind());
    }
}

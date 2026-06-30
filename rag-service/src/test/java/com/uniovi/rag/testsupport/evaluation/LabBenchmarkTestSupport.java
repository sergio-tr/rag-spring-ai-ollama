package com.uniovi.rag.testsupport.evaluation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.application.service.evaluation.LabBenchmarkDefaultModelResolver;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import java.util.Map;

public final class LabBenchmarkTestSupport {

    private LabBenchmarkTestSupport() {}

    public static LabBenchmarkDefaultModelResolver stubDefaultModelResolver(
            String chatModel, String embeddingModel) {
        ResolvedLlmConfigResolver configResolver = mock(ResolvedLlmConfigResolver.class);
        when(configResolver.resolve(any(), isNull(), isNull()))
                .thenReturn(
                        ResolvedLlmConfig.uniform(
                                LlmProvider.OLLAMA_NATIVE,
                                "http://localhost:11434",
                                chatModel,
                                embeddingModel,
                                null,
                                null,
                                null,
                                null,
                                null,
                                Map.of()));
        return new LabBenchmarkDefaultModelResolver(configResolver);
    }
}

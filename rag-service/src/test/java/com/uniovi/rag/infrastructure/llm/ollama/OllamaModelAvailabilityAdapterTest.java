package com.uniovi.rag.infrastructure.llm.ollama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OllamaModelAvailabilityAdapterTest {

    @Mock private OllamaApiClient apiClient;
    @Mock private ObjectProvider<OllamaApiClient> apiClientProvider;

    private OllamaModelAvailabilityAdapter adapter;

    @BeforeEach
    void setUp() {
        when(apiClientProvider.getIfAvailable()).thenReturn(apiClient);
        adapter = new OllamaModelAvailabilityAdapter(apiClientProvider);
    }

    @Test
    void cachesInstalledModelNamesAcrossProbes() throws Exception {
        when(apiClient.listModelNames()).thenReturn(Set.of("gemma3:4b", "llama3.1:8b"));

        assertThat(adapter.isModelPresent("gemma3:4b")).isTrue();
        assertThat(adapter.isModelPresent("llama3.1:8b")).isTrue();
        assertThat(adapter.isModelPresent("missing:latest")).isFalse();

        verify(apiClient, times(1)).listModelNames();
    }

    @Test
    void interruptedListDoesNotPoisonServletThreadForLaterWork() throws Exception {
        when(apiClient.listModelNames()).thenThrow(new InterruptedException("probe cancelled"));

        assertThat(adapter.isModelPresent("gemma3:4b")).isFalse();
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }

    @Test
    void ioFailureFallsBackWithoutInterruptingThread() throws Exception {
        when(apiClient.listModelNames()).thenThrow(new IOException("connection refused"));

        assertThat(adapter.isModelPresent("gemma3:4b")).isFalse();
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }

    @Test
    void matchesIsCaseInsensitivePrefix() {
        assertThat(OllamaModelAvailabilityAdapter.matches(Set.of("DeepSeek-V2:16b"), "deepseek-v2:16b"))
                .isTrue();
    }
}

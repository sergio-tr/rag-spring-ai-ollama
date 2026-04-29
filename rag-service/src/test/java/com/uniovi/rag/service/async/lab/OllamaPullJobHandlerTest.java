package com.uniovi.rag.service.async.lab;

import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.infrastructure.llm.ollama.OllamaModelProvisioningService;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.service.async.AsyncTaskMutationService;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OllamaPullJobHandlerTest {

    @Mock
    private OllamaModelProvisioningService ollamaModelProvisioningService;

    @Mock
    private AsyncTaskMutationService mutation;

    @Test
    void taskType_isOllamaPull() {
        OllamaPullJobHandler h = new OllamaPullJobHandler(ollamaModelProvisioningService);
        assertThat(h.taskType()).isEqualTo(AsyncTaskType.OLLAMA_PULL);
    }

    @Test
    void run_marksFailed_whenPayloadNull() {
        UUID taskId = UUID.randomUUID();
        AsyncTaskEntity task = task(taskId, null);
        OllamaPullJobHandler h = new OllamaPullJobHandler(ollamaModelProvisioningService);

        h.run(task, mutation);

        verify(mutation).markFailed(eq(taskId), eq("Missing model name"));
        verifyNoInteractions(ollamaModelProvisioningService);
    }

    @Test
    void run_marksFailed_whenModelKeyMissing() {
        UUID taskId = UUID.randomUUID();
        AsyncTaskEntity task = task(taskId, Map.of("other", "x"));
        OllamaPullJobHandler h = new OllamaPullJobHandler(ollamaModelProvisioningService);

        h.run(task, mutation);

        verify(mutation).markFailed(eq(taskId), eq("Missing model name"));
    }

    @Test
    void run_marksSucceeded_afterProvisioning() throws Exception {
        UUID taskId = UUID.randomUUID();
        AsyncTaskEntity task = task(taskId, Map.of(LabJobPayloadKeys.OLLAMA_MODEL, "  llama  "));
        OllamaPullJobHandler h = new OllamaPullJobHandler(ollamaModelProvisioningService);

        h.run(task, mutation);

        verify(ollamaModelProvisioningService).ensureModelPresent("llama");
        ArgumentCaptor<Map<String, Object>> res = ArgumentCaptor.forClass(Map.class);
        verify(mutation).markSucceeded(eq(taskId), res.capture());
        assertThat(res.getValue()).containsEntry("status", "ok").containsEntry("model", "llama");
    }

    @Test
    void run_marksFailed_onIOException() throws Exception {
        UUID taskId = UUID.randomUUID();
        AsyncTaskEntity task = task(taskId, Map.of(LabJobPayloadKeys.OLLAMA_MODEL, "m"));
        doThrow(new IOException("net down")).when(ollamaModelProvisioningService).ensureModelPresent("m");
        OllamaPullJobHandler h = new OllamaPullJobHandler(ollamaModelProvisioningService);

        h.run(task, mutation);

        verify(mutation).markFailed(eq(taskId), eq("net down"));
    }

    @Test
    void run_marksFailed_onInterruptedException() throws Exception {
        UUID taskId = UUID.randomUUID();
        AsyncTaskEntity task = task(taskId, Map.of(LabJobPayloadKeys.OLLAMA_MODEL, "m"));
        doThrow(new InterruptedException()).when(ollamaModelProvisioningService).ensureModelPresent("m");
        OllamaPullJobHandler h = new OllamaPullJobHandler(ollamaModelProvisioningService);

        h.run(task, mutation);

        verify(mutation).markFailed(eq(taskId), eq("Interrupted"));
    }

    private static AsyncTaskEntity task(UUID id, Map<String, Object> payload) {
        AsyncTaskEntity t = Mockito.mock(AsyncTaskEntity.class);
        when(t.getId()).thenReturn(id);
        when(t.getRequestPayload()).thenReturn(payload);
        return t;
    }
}

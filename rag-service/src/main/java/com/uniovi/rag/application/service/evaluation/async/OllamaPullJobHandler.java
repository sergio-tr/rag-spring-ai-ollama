package com.uniovi.rag.application.service.evaluation.async;

import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.infrastructure.llm.ollama.OllamaModelProvisioningService;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.application.service.async.AsyncTaskMutationService;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Component
class OllamaPullJobHandler implements LabJobHandler {

    private final OllamaModelProvisioningService ollamaModelProvisioningService;

    OllamaPullJobHandler(OllamaModelProvisioningService ollamaModelProvisioningService) {
        this.ollamaModelProvisioningService = ollamaModelProvisioningService;
    }

    @Override
    public AsyncTaskType taskType() {
        return AsyncTaskType.OLLAMA_PULL;
    }

    @Override
    public void run(AsyncTaskEntity task, AsyncTaskMutationService mutation) {
        UUID taskId = task.getId();
        Map<String, Object> payload = task.getRequestPayload();
        if (payload == null || payload.get(LabJobPayloadKeys.OLLAMA_MODEL) == null) {
            mutation.markFailed(taskId, "Missing model name");
            return;
        }
        String model = String.valueOf(payload.get(LabJobPayloadKeys.OLLAMA_MODEL)).trim();
        try {
            mutation.appendProgressLine(taskId, "Pulling model in Ollama: " + model);
            ollamaModelProvisioningService.ensureModelPresent(model);
            mutation.markSucceeded(taskId, Map.of("status", "ok", "model", model));
        } catch (IOException e) {
            mutation.markFailed(taskId, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            mutation.markFailed(taskId, "Interrupted");
        }
    }
}

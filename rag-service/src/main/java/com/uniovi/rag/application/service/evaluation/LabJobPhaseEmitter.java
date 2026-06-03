package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.domain.LabJobEventType;
import com.uniovi.rag.infrastructure.persistence.AsyncTaskRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emits at most one SSE event per logical phase key per job (avoids repeated dataset/reindex spam).
 */
@Service
public class LabJobPhaseEmitter {

    private static final String EMITTED_PHASES_KEY = "emittedPhases";

    private final AsyncTaskRepository asyncTaskRepository;
    private final LabJobEventService labJobEventService;

    public LabJobPhaseEmitter(AsyncTaskRepository asyncTaskRepository, LabJobEventService labJobEventService) {
        this.asyncTaskRepository = asyncTaskRepository;
        this.labJobEventService = labJobEventService;
    }

    @Transactional
    public void emitOnce(UUID taskId, String phaseKey, LabJobEventRequest request) {
        if (taskId == null || phaseKey == null || phaseKey.isBlank()) {
            return;
        }
        AsyncTaskEntity task = asyncTaskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return;
        }
        Map<String, Object> log = readEventLog(task);
        Set<String> emitted = readEmittedPhases(log);
        if (emitted.contains(phaseKey)) {
            return;
        }
        emitted.add(phaseKey);
        log.put(EMITTED_PHASES_KEY, emitted);
        task.setEventLogJson(log);
        asyncTaskRepository.save(task);
        labJobEventService.record(request);
    }

    @Transactional
    public void emitDatasetResolved(
            UUID taskId,
            UUID runId,
            UUID datasetId,
            String benchmarkKind,
            int questionCount,
            int presetCount) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("phase", "DATASET");
        if (datasetId != null) {
            payload.put("datasetId", datasetId.toString());
        }
        if (benchmarkKind != null) {
            payload.put("benchmarkKind", benchmarkKind);
        }
        payload.put("questionCount", questionCount);
        payload.put("presetCount", presetCount);
        emitOnce(
                taskId,
                "dataset-resolved",
                LabJobEventRequest.of(
                                taskId,
                                LabJobEventType.DATASET_RESOLVED,
                                "Dataset ready · "
                                        + questionCount
                                        + " questions"
                                        + (presetCount > 0 ? " · " + presetCount + " presets" : ""))
                        .withPayload(payload)
                        .withScope(null, runId, null, null, null, null, null, null, null));
    }

    @Transactional
    public void emitKnowledgeBaseChecked(
            UUID taskId, UUID runId, UUID corpusId, int readyCount, int totalCount) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("phase", "KNOWLEDGE_BASE");
        if (corpusId != null) {
            payload.put("corpusId", corpusId.toString());
            payload.put("knowledgeBaseId", corpusId.toString());
        }
        payload.put("readyCount", readyCount);
        payload.put("totalCount", totalCount);
        emitOnce(
                taskId,
                "knowledge-base-checked",
                LabJobEventRequest.of(
                                taskId,
                                LabJobEventType.KNOWLEDGE_BASE_CHECKED,
                                "Knowledge base ready · " + readyCount + "/" + totalCount + " documents")
                        .withPayload(payload)
                        .withScope(null, runId, null, null, null, null, null, null, null));
    }

    @Transactional
    public void emitAutoReindexLock(UUID taskId, UUID runId, UUID projectId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("phase", "INDEXING");
        payload.put("technicalMessage", "Auto-reindex lock acquired for projectId=" + projectId);
        if (projectId != null) {
            payload.put("projectId", projectId.toString());
        }
        emitOnce(
                taskId,
                "auto-reindex-lock",
                LabJobEventRequest.of(taskId, LabJobEventType.STARTED, "Preparing project index")
                        .withPayload(payload)
                        .withScope(null, runId, null, null, null, null, null, null, null));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readEventLog(AsyncTaskEntity task) {
        Map<String, Object> raw = task.getEventLogJson();
        if (raw == null || raw.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(raw);
    }

    @SuppressWarnings("unchecked")
    private static Set<String> readEmittedPhases(Map<String, Object> log) {
        Object raw = log.get(EMITTED_PHASES_KEY);
        if (raw instanceof Iterable<?> iterable) {
            Set<String> out = new LinkedHashSet<>();
            for (Object item : iterable) {
                if (item != null) {
                    out.add(String.valueOf(item));
                }
            }
            return out;
        }
        return new LinkedHashSet<>();
    }
}

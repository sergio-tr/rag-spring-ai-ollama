package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.domain.LabJobEventType;
import java.util.Map;
import java.util.UUID;

/** Mutable builder input for {@link LabJobEventService#record(LabJobEventRequest)}. */
public record LabJobEventRequest(
        UUID taskId,
        LabJobEventType type,
        String message,
        Map<String, Object> payload,
        UUID campaignId,
        UUID runId,
        String itemId,
        Integer globalCompletedItems,
        Integer globalTotalItems,
        Integer runCompletedItems,
        Integer runTotalItems,
        String currentModelId,
        String currentPresetCode) {

    public static LabJobEventRequest of(UUID taskId, LabJobEventType type, String message) {
        return new LabJobEventRequest(
                taskId, type, message, Map.of(), null, null, null, null, null, null, null, null, null);
    }

    public LabJobEventRequest withPayload(Map<String, Object> payload) {
        return new LabJobEventRequest(
                taskId,
                type,
                message,
                payload != null ? payload : Map.of(),
                campaignId,
                runId,
                itemId,
                globalCompletedItems,
                globalTotalItems,
                runCompletedItems,
                runTotalItems,
                currentModelId,
                currentPresetCode);
    }

    public LabJobEventRequest withScope(
            UUID campaignId,
            UUID runId,
            String itemId,
            Integer globalCompleted,
            Integer globalTotal,
            Integer runCompleted,
            Integer runTotal,
            String modelId,
            String presetCode) {
        return new LabJobEventRequest(
                taskId,
                type,
                message,
                payload,
                campaignId,
                runId,
                itemId,
                globalCompleted,
                globalTotal,
                runCompleted,
                runTotal,
                modelId,
                presetCode);
    }
}

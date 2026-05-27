package com.uniovi.rag.interfaces.rest.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Single Lab job event for SSE streams and resume ({@code ?since=eventId}). */
public record LabJobEventDto(
        long eventId,
        UUID jobId,
        String type,
        String status,
        String progress,
        String message,
        Instant timestamp,
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

    /** Backward-compatible constructor for tests and legacy call sites. */
    public LabJobEventDto(
            long eventId,
            UUID jobId,
            String type,
            String status,
            String progress,
            String message,
            Instant timestamp,
            Map<String, Object> payload) {
        this(
                eventId,
                jobId,
                type,
                status,
                progress,
                message,
                timestamp,
                payload != null ? payload : Map.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    public boolean terminal() {
        return type != null
                && (type.equals("FAILED")
                        || type.equals("CANCELLED")
                        || type.equals("RUN_COMPLETED")
                        || type.equals("CAMPAIGN_COMPLETED")
                        || type.equals("COMPLETED"));
    }
}

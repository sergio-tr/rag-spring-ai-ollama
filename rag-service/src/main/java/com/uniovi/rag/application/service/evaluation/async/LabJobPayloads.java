package com.uniovi.rag.application.service.evaluation.async;

import java.util.Map;
import java.util.UUID;

final class LabJobPayloads {

    private LabJobPayloads() {
    }

    static UUID evaluationRunId(Map<String, Object> payload) {
        return parseUuid(payload, LabJobPayloadKeys.EVALUATION_RUN_ID);
    }

    static UUID campaignId(Map<String, Object> payload) {
        return parseUuid(payload, LabJobPayloadKeys.CAMPAIGN_ID);
    }

    private static UUID parseUuid(Map<String, Object> payload, String key) {
        if (payload == null) {
            return null;
        }
        Object v = payload.get(key);
        if (v == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(v));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}

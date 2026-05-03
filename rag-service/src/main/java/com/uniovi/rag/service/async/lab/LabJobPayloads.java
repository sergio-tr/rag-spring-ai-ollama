package com.uniovi.rag.service.async.lab;

import java.util.Map;
import java.util.UUID;

final class LabJobPayloads {

    private LabJobPayloads() {
    }

    static UUID evaluationRunId(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        Object v = payload.get(LabJobPayloadKeys.EVALUATION_RUN_ID);
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

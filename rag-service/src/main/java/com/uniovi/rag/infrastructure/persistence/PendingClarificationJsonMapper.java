package com.uniovi.rag.infrastructure.persistence;

import com.uniovi.rag.domain.runtime.clarification.ClarificationQuestionKind;
import com.uniovi.rag.domain.runtime.clarification.PendingClarificationState;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Maps {@link PendingClarificationState} to/from the frozen JSON shape in {@code pending_clarification_jsonb}.
 */
public final class PendingClarificationJsonMapper {

    private PendingClarificationJsonMapper() {
    }

    public static Map<String, Object> toMap(PendingClarificationState s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("clarificationStateVersion", s.clarificationStateVersion());
        m.put("originatingUserMessageId", s.originatingUserMessageId().toString());
        m.put("baseQueryTextForClarification", s.baseQueryTextForClarification());
        m.put("clarificationQuestionText", s.clarificationQuestionText());
        m.put("requestedFields", new ArrayList<>(s.requestedFields()));
        m.put("clarificationReasons", new ArrayList<>(s.clarificationReasons()));
        m.put("createdAt", s.createdAt().toString());
        m.put("correlationId", s.correlationId());
        m.put("questionKind", s.questionKind().name());
        return m;
    }

    /**
     * @return parsed state or {@code null} if validation fails (caller treats as invalid load)
     */
    public static PendingClarificationState fromMap(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            int version = readInt(raw, "clarificationStateVersion", -1);
            if (version != PendingClarificationState.SCHEMA_VERSION) {
                return null;
            }
            UUID originating = UUID.fromString(readString(raw, "originatingUserMessageId"));
            String base = readString(raw, "baseQueryTextForClarification");
            String qText = readString(raw, "clarificationQuestionText");
            List<String> requested = readStringList(raw, "requestedFields");
            List<String> reasons = readStringList(raw, "clarificationReasons");
            Instant createdAt = Instant.parse(readString(raw, "createdAt"));
            String correlationId = readString(raw, "correlationId");
            ClarificationQuestionKind kind = ClarificationQuestionKind.valueOf(readString(raw, "questionKind"));
            if (!kind.templateText().equals(qText)) {
                return null;
            }
            return new PendingClarificationState(
                    version,
                    originating,
                    base,
                    qText,
                    kind,
                    requested,
                    reasons,
                    createdAt,
                    correlationId);
        } catch (Exception e) {
            return null;
        }
    }

    private static int readInt(Map<String, Object> m, String key, int defaultVal) {
        Object v = m.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        return defaultVal;
    }

    private static String readString(Map<String, Object> m, String key) {
        Object v = Objects.requireNonNull(m.get(key), key);
        return v.toString();
    }

    @SuppressWarnings("unchecked")
    private static List<String> readStringList(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) {
            throw new IllegalArgumentException("missing " + key);
        }
        if (!(v instanceof List<?> list)) {
            throw new IllegalArgumentException(key + " not a list");
        }
        List<String> out = new ArrayList<>();
        for (Object o : list) {
            out.add(o == null ? "" : o.toString());
        }
        return out;
    }
}

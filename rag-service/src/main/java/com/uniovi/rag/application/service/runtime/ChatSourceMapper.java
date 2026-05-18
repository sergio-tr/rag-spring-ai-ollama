package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.application.result.chat.ChatSource;
import com.uniovi.rag.interfaces.rest.dto.ChatSourceDto;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Normalizes legacy map-shaped sources and maps internal <-> DTO shapes.
 */
public final class ChatSourceMapper {

    private ChatSourceMapper() {}

    public static List<ChatSource> fromLegacyMaps(List<Map<String, Object>> legacy) {
        if (legacy == null || legacy.isEmpty()) {
            return List.of();
        }
        List<ChatSource> out = new ArrayList<>();
        for (Map<String, Object> row : legacy) {
            ChatSource s = fromLegacyMap(row);
            if (s != null) out.add(s);
        }
        return List.copyOf(out);
    }

    public static ChatSource fromLegacyMap(Map<String, Object> row) {
        if (row == null || row.isEmpty()) return null;
        String filename = str(row.get("filename"));
        String documentId =
                firstString(row, "documentId", "document_id", "projectDocumentId", "project_document_id");
        String projectDocumentId = str(row.get("projectDocumentId"));
        Integer chunkIndex = intOrNull(first(row, "chunkIndex", "chunk_index"));
        Double distance = doubleOrNull(row.get("distance"));
        String snippet = str(row.get("snippet"));
        String detectedDate = str(row.get("detectedDate"));
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata =
                row.get("metadata") instanceof Map<?, ?> m ? new LinkedHashMap<>((Map<String, Object>) m) : null;
        return new ChatSource(
                documentId,
                projectDocumentId,
                filename,
                snippet,
                distance,
                "distance",
                chunkIndex,
                detectedDate,
                metadata != null && !metadata.isEmpty() ? Map.copyOf(metadata) : null);
    }

    public static List<ChatSourceDto> toDtos(List<ChatSource> sources) {
        if (sources == null || sources.isEmpty()) return List.of();
        return sources.stream().map(ChatSourceMapper::toDto).toList();
    }

    public static ChatSourceDto toDto(ChatSource s) {
        if (s == null) return null;
        return new ChatSourceDto(
                s.documentId(),
                s.projectDocumentId(),
                s.filename(),
                s.snippet(),
                s.distance(),
                "distance",
                s.chunkIndex(),
                s.detectedDate(),
                s.metadata());
    }

    public static List<Map<String, Object>> toPersistedMaps(List<ChatSourceDto> dtos) {
        if (dtos == null || dtos.isEmpty()) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (ChatSourceDto d : dtos) {
            if (d == null) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            if (d.documentId() != null) m.put("documentId", d.documentId());
            if (d.projectDocumentId() != null) m.put("projectDocumentId", d.projectDocumentId());
            if (d.filename() != null) m.put("filename", d.filename());
            if (d.snippet() != null) m.put("snippet", d.snippet());
            if (d.distance() != null) m.put("distance", d.distance());
            m.put("distanceLabel", "distance");
            if (d.chunkIndex() != null) m.put("chunkIndex", d.chunkIndex());
            if (d.detectedDate() != null) m.put("detectedDate", d.detectedDate());
            if (d.metadata() != null && !d.metadata().isEmpty()) m.put("metadata", d.metadata());
            out.add(Map.copyOf(m));
        }
        return List.copyOf(out);
    }

    /** Writes the stable persisted JSON shape without depending on REST DTOs. */
    public static List<Map<String, Object>> toPersistedMapsFromInternal(List<ChatSource> sources) {
        if (sources == null || sources.isEmpty()) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (ChatSource s : sources) {
            if (s == null) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            if (s.documentId() != null) m.put("documentId", s.documentId());
            if (s.projectDocumentId() != null) m.put("projectDocumentId", s.projectDocumentId());
            if (s.filename() != null) m.put("filename", s.filename());
            if (s.snippet() != null) m.put("snippet", s.snippet());
            if (s.distance() != null) m.put("distance", s.distance());
            m.put("distanceLabel", "distance");
            if (s.chunkIndex() != null) m.put("chunkIndex", s.chunkIndex());
            if (s.detectedDate() != null) m.put("detectedDate", s.detectedDate());
            if (s.metadata() != null && !s.metadata().isEmpty()) m.put("metadata", s.metadata());
            out.add(Map.copyOf(m));
        }
        return List.copyOf(out);
    }

    private static Object first(Map<String, Object> row, String... keys) {
        for (String k : keys) {
            if (row.containsKey(k)) return row.get(k);
        }
        return null;
    }

    private static String firstString(Map<String, Object> row, String... keys) {
        for (String k : keys) {
            String s = str(row.get(k));
            if (s != null && !s.isBlank()) return s;
        }
        return null;
    }

    private static String str(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private static Integer intOrNull(Object v) {
        if (v == null) return null;
        try {
            return v instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(v));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Double doubleOrNull(Object v) {
        if (v == null) return null;
        try {
            return v instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(v));
        } catch (Exception ignored) {
            return null;
        }
    }
}


package com.uniovi.rag.interfaces.rest.mapper;

import com.uniovi.rag.application.result.chat.ChatSource;
import com.uniovi.rag.interfaces.rest.dto.ChatSourceDto;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Maps chat retrieval sources between application results and REST DTOs. */
public final class ChatSourceRestMapper {

    private ChatSourceRestMapper() {}

    public static List<ChatSourceDto> toDtos(List<ChatSource> sources) {
        if (sources == null || sources.isEmpty()) {
            return List.of();
        }
        return sources.stream().map(ChatSourceRestMapper::toDto).toList();
    }

    public static ChatSourceDto toDto(ChatSource s) {
        if (s == null) {
            return null;
        }
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
        if (dtos == null || dtos.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (ChatSourceDto d : dtos) {
            if (d == null) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            if (d.documentId() != null) {
                m.put("documentId", d.documentId());
            }
            if (d.projectDocumentId() != null) {
                m.put("projectDocumentId", d.projectDocumentId());
            }
            if (d.filename() != null) {
                m.put("filename", d.filename());
            }
            if (d.snippet() != null) {
                m.put("snippet", d.snippet());
            }
            if (d.distance() != null) {
                m.put("distance", d.distance());
            }
            m.put("distanceLabel", "distance");
            if (d.chunkIndex() != null) {
                m.put("chunkIndex", d.chunkIndex());
            }
            if (d.detectedDate() != null) {
                m.put("detectedDate", d.detectedDate());
            }
            if (d.metadata() != null && !d.metadata().isEmpty()) {
                m.put("metadata", d.metadata());
            }
            out.add(Map.copyOf(m));
        }
        return List.copyOf(out);
    }
}

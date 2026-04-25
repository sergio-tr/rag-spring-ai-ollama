package com.uniovi.rag.infrastructure.persistence.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.domain.config.runtime.ResolvedConfigSnapshot;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.infrastructure.persistence.jpa.ResolvedConfigSnapshotEntity;
import com.uniovi.rag.interfaces.rest.dto.ResolvedConfigSnapshotResponse;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Single owner of {@code resolved_config_snapshot} JSON column shapes. Embeds {@code schema_version}
 * and {@code creatingUserId} under {@code provenance_jsonb} for forward-compatible readers.
 */
@Component
public class ResolvedConfigSnapshotEntityMapper {

    /** Bump when adding new required JSON keys to persisted snapshot blobs. */
    public static final int SNAPSHOT_SCHEMA_VERSION = 1;

    public static final String PROVENANCE_SCHEMA_VERSION = "schema_version";
    public static final String PROVENANCE_CREATING_USER_ID = "creatingUserId";
    public static final String PROVENANCE_CORRELATION_ID = "correlationId";
    public static final String PROVENANCE_PROJECT_ID = "projectId";

    private static final TypeReference<Map<String, Object>> MAP_STRING_OBJECT = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public ResolvedConfigSnapshotEntityMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ResolvedConfigSnapshotEntity toNewEntity(
            ResolvedRuntimeConfig resolved,
            ResolvedConfigSnapshot domainSnapshot,
            UUID creatingUserId,
            String configHash,
            Optional<UUID> conversationId,
            Optional<UUID> messageId,
            Optional<UUID> jobId,
            Optional<String> correlationId) {
        return toNewEntity(
                resolved,
                domainSnapshot,
                creatingUserId,
                configHash,
                conversationId,
                messageId,
                jobId,
                correlationId,
                Optional.empty(),
                null);
    }

    /**
     * @param knowledgeBuildProjectionNested merged under {@link com.uniovi.rag.application.service.knowledge.KnowledgeBuildProjectionMapper#PAYLOAD_KEY}
     */
    public ResolvedConfigSnapshotEntity toNewEntity(
            ResolvedRuntimeConfig resolved,
            ResolvedConfigSnapshot domainSnapshot,
            UUID creatingUserId,
            String configHash,
            Optional<UUID> conversationId,
            Optional<UUID> messageId,
            Optional<UUID> jobId,
            Optional<String> correlationId,
            Optional<UUID> projectId,
            Map<String, Object> knowledgeBuildProjectionNested) {
        ResolvedConfigSnapshotEntity e = ResolvedConfigSnapshotEntity.newForInsert();
        e.setCreatedAt(Instant.now());
        Map<String, Object> payload = new LinkedHashMap<>(resolved.resolvedCoreConfig().toValueMap());
        if (knowledgeBuildProjectionNested != null && !knowledgeBuildProjectionNested.isEmpty()) {
            payload.put(
                    com.uniovi.rag.application.service.knowledge.KnowledgeBuildProjectionMapper.PAYLOAD_KEY,
                    knowledgeBuildProjectionNested);
        }
        e.setPayloadJsonb(payload);
        e.setCapabilitySetJsonb(toJsonMap(resolved.capabilitySet()));
        e.setCompatibilityResultJsonb(toJsonMap(resolved.compatibility()));
        e.setReindexImpactJsonb(toJsonMap(resolved.reindexImpact()));
        e.setSystemPromptLayersJsonb(toJsonMap(resolved.systemPromptLayers()));
        e.setEffectiveSystemPrompt(
                resolved.effectiveSystemPrompt() != null && !resolved.effectiveSystemPrompt().isBlank()
                        ? resolved.effectiveSystemPrompt()
                        : "");
        e.setConfigHash(configHash);
        conversationId.ifPresent(e::setConversationId);
        messageId.ifPresent(e::setMessageId);
        jobId.ifPresent(e::setJobId);
        e.setProvenanceJsonb(buildProvenanceJson(domainSnapshot, creatingUserId, correlationId, projectId));
        return e;
    }

    public ResolvedConfigSnapshotResponse toResponse(ResolvedConfigSnapshotEntity entity) {
        return new ResolvedConfigSnapshotResponse(
                entity.getId(),
                entity.getCreatedAt(),
                copyMap(entity.getPayloadJsonb()),
                copyMap(entity.getCapabilitySetJsonb()),
                copyMap(entity.getCompatibilityResultJsonb()),
                copyMap(entity.getReindexImpactJsonb()),
                copyMap(entity.getSystemPromptLayersJsonb()),
                entity.getEffectiveSystemPrompt() != null ? entity.getEffectiveSystemPrompt() : "",
                copyMap(entity.getProvenanceJsonb()),
                entity.getConfigHash() != null ? entity.getConfigHash() : "",
                entity.getConversationId(),
                entity.getMessageId(),
                entity.getJobId());
    }

    private Map<String, Object> buildProvenanceJson(
            ResolvedConfigSnapshot domainSnapshot,
            UUID creatingUserId,
            Optional<String> correlationId,
            Optional<UUID> projectId) {
        Map<String, Object> prov = new LinkedHashMap<>();
        if (domainSnapshot.provenance() != null) {
            Map<String, Object> fromDomain = toJsonMap(domainSnapshot.provenance());
            if (fromDomain != null) {
                prov.putAll(fromDomain);
            }
        }
        prov.put(PROVENANCE_SCHEMA_VERSION, SNAPSHOT_SCHEMA_VERSION);
        prov.put(PROVENANCE_CREATING_USER_ID, creatingUserId.toString());
        correlationId.filter(s -> !s.isBlank()).ifPresent(c -> prov.put(PROVENANCE_CORRELATION_ID, c));
        projectId.ifPresent(pid -> prov.put(PROVENANCE_PROJECT_ID, pid.toString()));
        return prov;
    }

    private Map<String, Object> toJsonMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        return objectMapper.convertValue(value, MAP_STRING_OBJECT);
    }

    private static Map<String, Object> copyMap(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(raw);
    }
}

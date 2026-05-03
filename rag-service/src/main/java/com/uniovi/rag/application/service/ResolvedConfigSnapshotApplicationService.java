package com.uniovi.rag.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.config.ConfigResolverService;
import com.uniovi.rag.application.config.RuntimeConfigResolutionInput;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.runtime.ConfigProfileType;
import com.uniovi.rag.domain.config.runtime.ResolvedConfigSnapshot;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.infrastructure.config.ResolvedRuntimeConfigHasher;
import com.uniovi.rag.infrastructure.persistence.ResolvedConfigSnapshotRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ResolvedConfigSnapshotEntity;
import com.uniovi.rag.infrastructure.persistence.mapper.ResolvedConfigSnapshotEntityMapper;
import com.uniovi.rag.infrastructure.persistence.mapper.ResolvedConfigSnapshotEntityMapper.ResolvedConfigSnapshotInsertContext;
import com.uniovi.rag.interfaces.rest.dto.CreateResolvedConfigSnapshotRequest;
import com.uniovi.rag.interfaces.rest.dto.ResolvedConfigSnapshotCreatedResponse;
import com.uniovi.rag.interfaces.rest.dto.ResolvedConfigSnapshotResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Persists resolved configuration snapshots after {@link ConfigResolverService#resolve}.
 */
@Service
public class ResolvedConfigSnapshotApplicationService {

    private final ConfigResolverService configResolverService;
    private final ResolvedConfigSnapshotRepository resolvedConfigSnapshotRepository;
    private final ResolvedConfigSnapshotEntityMapper resolvedConfigSnapshotEntityMapper;
    private final ObjectMapper objectMapper;

    public ResolvedConfigSnapshotApplicationService(
            ConfigResolverService configResolverService,
            ResolvedConfigSnapshotRepository resolvedConfigSnapshotRepository,
            ResolvedConfigSnapshotEntityMapper resolvedConfigSnapshotEntityMapper,
            ObjectMapper objectMapper) {
        this.configResolverService = configResolverService;
        this.resolvedConfigSnapshotRepository = resolvedConfigSnapshotRepository;
        this.resolvedConfigSnapshotEntityMapper = resolvedConfigSnapshotEntityMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ResolvedConfigSnapshotCreatedResponse createFromRequest(UUID userId, CreateResolvedConfigSnapshotRequest req) {
        RuntimeConfigResolutionInput input = toResolutionInput(userId, req);
        ResolvedRuntimeConfig resolved = configResolverService.resolve(input);
        ResolvedConfigSnapshot domainSnapshot = configResolverService.snapshot(resolved);
        String hash = ResolvedRuntimeConfigHasher.sha256Hex(resolved);
        ResolvedConfigSnapshotEntity entity =
                resolvedConfigSnapshotEntityMapper.toNewEntity(
                        resolved,
                        domainSnapshot,
                        ResolvedConfigSnapshotInsertContext.of(
                                userId,
                                hash,
                                Optional.ofNullable(req.conversationId()),
                                Optional.ofNullable(req.messageId()),
                                Optional.ofNullable(req.jobId()),
                                Optional.ofNullable(req.correlationId()).filter(s -> !s.isBlank()),
                                Optional.of(req.projectId())));
        entity = resolvedConfigSnapshotRepository.save(entity);
        return new ResolvedConfigSnapshotCreatedResponse(entity.getId(), entity.getConfigHash(), entity.getCreatedAt());
    }

    /**
     * Default resolved configuration row for corpus ingestion (no {@code knowledgeBuildProjection} payload key).
     */
    @Transactional
    public ResolvedConfigSnapshotEntity persistIngestionDefaultSnapshot(
            UUID userId, UUID projectId, Optional<UUID> conversationId) {
        RuntimeConfigResolutionInput input =
                new RuntimeConfigResolutionInput(
                        userId,
                        projectId,
                        conversationId,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Set.of(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty());
        ResolvedRuntimeConfig resolved = configResolverService.resolve(input);
        ResolvedConfigSnapshot domainSnapshot = configResolverService.snapshot(resolved);
        String hash = ResolvedRuntimeConfigHasher.sha256Hex(resolved, null);
        ResolvedConfigSnapshotEntity entity =
                resolvedConfigSnapshotEntityMapper.toNewEntity(
                        resolved,
                        domainSnapshot,
                        ResolvedConfigSnapshotInsertContext.of(
                                userId,
                                hash,
                                conversationId,
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.of(projectId)));
        return resolvedConfigSnapshotRepository.save(entity);
    }

    /**
     * Persists a resolved snapshot for knowledge execute-without-pin; {@code knowledgeNested} is merged under
     * {@link com.uniovi.rag.application.service.knowledge.KnowledgeBuildProjectionMapper#PAYLOAD_KEY}.
     */
    @Transactional
    public ResolvedConfigSnapshotEntity persistForKnowledgeExecute(
            ResolvedRuntimeConfig resolved,
            UUID userId,
            UUID projectId,
            Optional<UUID> conversationId,
            Optional<String> correlationId,
            Map<String, Object> knowledgeNested) {
        ResolvedConfigSnapshot domainSnapshot = configResolverService.snapshot(resolved);
        String hash = ResolvedRuntimeConfigHasher.sha256Hex(resolved, knowledgeNested);
        ResolvedConfigSnapshotEntity entity =
                resolvedConfigSnapshotEntityMapper.toNewEntity(
                        resolved,
                        domainSnapshot,
                        ResolvedConfigSnapshotInsertContext.of(
                                        userId,
                                        hash,
                                        conversationId,
                                        Optional.empty(),
                                        Optional.empty(),
                                        correlationId,
                                        Optional.of(projectId))
                                .withKnowledge(knowledgeNested));
        return resolvedConfigSnapshotRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public ResolvedConfigSnapshotEntity getValidatedSnapshotForKnowledgePin(UUID projectId, UUID userId, UUID snapshotId) {
        ResolvedConfigSnapshotEntity entity =
                resolvedConfigSnapshotRepository
                        .findById(snapshotId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        UUID owner = readCreatingUserId(entity);
        if (owner == null || !owner.equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        UUID provProject = readProjectId(entity);
        if (provProject == null || !provProject.equals(projectId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return entity;
    }

    @Transactional(readOnly = true)
    public ResolvedConfigSnapshotResponse getByIdForUser(UUID userId, UUID snapshotId) {
        ResolvedConfigSnapshotEntity entity =
                resolvedConfigSnapshotRepository
                        .findById(snapshotId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        UUID owner = readCreatingUserId(entity);
        if (owner == null || !owner.equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return resolvedConfigSnapshotEntityMapper.toResponse(entity);
    }

    private RuntimeConfigResolutionInput toResolutionInput(UUID userId, CreateResolvedConfigSnapshotRequest req) {
        JsonNode override =
                req.runtimeOverride() == null || req.runtimeOverride().isEmpty()
                        ? null
                        : objectMapper.valueToTree(req.runtimeOverride());
        Set<ConfigProfileType> touched = parseTouchedProfileTypes(req.touchedProfileTypes());
        CapabilitySet baseline =
                req.baselineCapabilitySnapshot() != null
                        ? req.baselineCapabilitySnapshot().toCapabilitySet()
                        : null;
        return new RuntimeConfigResolutionInput(
                userId,
                req.projectId(),
                Optional.ofNullable(req.conversationId()),
                Optional.ofNullable(req.presetId()),
                Optional.empty(),
                Optional.ofNullable(override),
                touched,
                Optional.ofNullable(baseline),
                Optional.empty(),
                Optional.ofNullable(req.correlationId()).filter(s -> !s.isBlank()));
    }

    private static Set<ConfigProfileType> parseTouchedProfileTypes(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Set.of();
        }
        EnumSet<ConfigProfileType> set = EnumSet.noneOf(ConfigProfileType.class);
        for (String s : raw) {
            if (s == null || s.isBlank()) {
                continue;
            }
            set.add(ConfigProfileType.valueOf(s.trim()));
        }
        return set;
    }

    private static UUID readCreatingUserId(ResolvedConfigSnapshotEntity entity) {
        if (entity.getProvenanceJsonb() == null) {
            return null;
        }
        Object raw = entity.getProvenanceJsonb().get(ResolvedConfigSnapshotEntityMapper.PROVENANCE_CREATING_USER_ID);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static UUID readProjectId(ResolvedConfigSnapshotEntity entity) {
        if (entity.getProvenanceJsonb() == null) {
            return null;
        }
        Object raw = entity.getProvenanceJsonb().get(ResolvedConfigSnapshotEntityMapper.PROVENANCE_PROJECT_ID);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

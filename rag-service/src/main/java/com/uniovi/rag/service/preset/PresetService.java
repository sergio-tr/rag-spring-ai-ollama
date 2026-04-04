package com.uniovi.rag.service.preset;

import com.uniovi.rag.interfaces.rest.dto.CreateRagPresetRequest;
import com.uniovi.rag.interfaces.rest.dto.RagPresetDto;
import com.uniovi.rag.interfaces.rest.dto.UpdateRagPresetRequest;
import com.uniovi.rag.infrastructure.persistence.jpa.RagPresetEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.infrastructure.persistence.RagPresetRepository;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.service.config.RagConfigValueSanitizer;
import com.uniovi.rag.service.config.UserProjectConfigurationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * User-owned named configuration snapshots; system presets are read-only through this API.
 * Project creation can copy {@linkplain RagPresetEntity#getValues() preset values} into the project-level
 * RAG config row (see {@link #applyInitialPresetToProject(UUID, UUID, UUID)}).
 */
@Service
public class PresetService {

    private final RagPresetRepository ragPresetRepository;
    private final UserRepository userRepository;
    private final UserProjectConfigurationService userProjectConfigurationService;

    public PresetService(
            RagPresetRepository ragPresetRepository,
            UserRepository userRepository,
            UserProjectConfigurationService userProjectConfigurationService) {
        this.ragPresetRepository = ragPresetRepository;
        this.userRepository = userRepository;
        this.userProjectConfigurationService = userProjectConfigurationService;
    }

    @Transactional(readOnly = true)
    public List<RagPresetDto> list(UUID userId) {
        return ragPresetRepository.findVisibleForUser(userId).stream().map(PresetService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public RagPresetDto get(UUID userId, UUID presetId) {
        return toDto(requireVisiblePreset(userId, presetId));
    }

    @Transactional
    public RagPresetDto create(UUID userId, CreateRagPresetRequest req) {
        UserEntity owner = userRepository.findById(userId).orElseThrow();
        Instant now = Instant.now();
        RagPresetEntity e = RagPresetEntity.newUserOwned(
                owner,
                req.name().trim(),
                req.description() != null && !req.description().isBlank() ? req.description().trim() : null,
                req.tags() != null ? new ArrayList<>(req.tags()) : new ArrayList<>(),
                RagConfigValueSanitizer.sanitize(req.values() != null ? req.values() : Map.of()),
                now,
                now);
        return toDto(ragPresetRepository.save(e));
    }

    @Transactional
    public RagPresetDto update(UUID userId, UUID presetId, UpdateRagPresetRequest req) {
        RagPresetEntity e = ragPresetRepository.findByIdAndOwner_Id(presetId, userId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Preset not found or not owned by user"));
        if (e.isSystem()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "System presets cannot be modified");
        }
        if (req.name() != null && !req.name().isBlank()) {
            e.setName(req.name().trim());
        }
        if (req.description() != null) {
            e.setDescription(req.description().isBlank() ? null : req.description().trim());
        }
        if (req.tags() != null) {
            e.setTags(new ArrayList<>(req.tags()));
        }
        if (req.values() != null) {
            e.setValues(RagConfigValueSanitizer.sanitize(req.values()));
        }
        e.setUpdatedAt(Instant.now());
        return toDto(ragPresetRepository.save(e));
    }

    @Transactional
    public void delete(UUID userId, UUID presetId) {
        RagPresetEntity e = ragPresetRepository.findByIdAndOwner_Id(presetId, userId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Preset not found or not owned by user"));
        if (e.isSystem()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "System presets cannot be deleted");
        }
        ragPresetRepository.delete(e);
    }

    /**
     * Copies sanitized preset values into the project's active PROJECT-level RAG configuration.
     */
    @Transactional
    public void applyInitialPresetToProject(UUID userId, UUID projectId, UUID presetId) {
        RagPresetEntity preset = requireVisiblePreset(userId, presetId);
        userProjectConfigurationService.putProjectConfig(
                userId, projectId, RagConfigValueSanitizer.sanitize(preset.getValues()));
    }

    /**
     * Resolves a preset visible to the user (owned or system).
     */
    @Transactional(readOnly = true)
    public RagPresetEntity requireVisiblePreset(UUID userId, UUID presetId) {
        RagPresetEntity entity =
                ragPresetRepository.findById(presetId).orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Preset not found"));
        if (entity.isSystem()) {
            return entity;
        }
        if (entity.getOwner() == null || !userId.equals(entity.getOwner().getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Preset not found");
        }
        return entity;
    }

    private static RagPresetDto toDto(RagPresetEntity e) {
        return new RagPresetDto(
                e.getId(),
                e.getName(),
                e.getDescription(),
                e.getTags() != null ? List.copyOf(e.getTags()) : List.of(),
                e.getValues() != null ? Map.copyOf(e.getValues()) : Map.of(),
                e.isSystem(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }
}

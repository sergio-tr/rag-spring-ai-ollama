package com.uniovi.rag.application.service.preset;

import com.uniovi.rag.domain.preset.UserRagPreset;
import com.uniovi.rag.interfaces.rest.dto.CreateRagPresetRequest;
import com.uniovi.rag.interfaces.rest.dto.PresetProfileRefDto;
import com.uniovi.rag.interfaces.rest.dto.RagPresetDto;
import com.uniovi.rag.interfaces.rest.dto.UpdateRagPresetRequest;
import com.uniovi.rag.infrastructure.persistence.ConfigProfileRepository;
import com.uniovi.rag.infrastructure.persistence.RagPresetRepository;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ConfigProfileEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.RagPresetEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.RagPresetProfileRefEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.application.service.AuditApplicationService;
import com.uniovi.rag.application.service.config.RagConfigValueSanitizer;
import com.uniovi.rag.application.service.config.UserProjectConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * User-owned named configuration snapshots; system presets are read-only through this API.
 * Project creation can copy {@linkplain RagPresetEntity#getValues() preset values} into the project-level
 * RAG config row (see {@link #applyInitialPresetToProject(UUID, UUID, UUID)}).
 */
@Service
public class PresetService {

    private static final String AUDIT_RESOURCE_TYPE_RAG_PRESET = "rag_preset";

    private final RagPresetRepository ragPresetRepository;
    private final UserRepository userRepository;
    private final UserProjectConfigurationService userProjectConfigurationService;
    private final AuditApplicationService auditApplicationService;
    private final ConfigProfileRepository configProfileRepository;

    /** Lazy self-reference so {@link #requireVisiblePreset} hits the Spring proxy when invoked from this bean. */
    private PresetService self;

    public PresetService(
            RagPresetRepository ragPresetRepository,
            UserRepository userRepository,
            UserProjectConfigurationService userProjectConfigurationService,
            AuditApplicationService auditApplicationService,
            ConfigProfileRepository configProfileRepository) {
        this.ragPresetRepository = ragPresetRepository;
        this.userRepository = userRepository;
        this.userProjectConfigurationService = userProjectConfigurationService;
        this.auditApplicationService = auditApplicationService;
        this.configProfileRepository = configProfileRepository;
    }

    @Autowired
    void setTransactionalSelf(@Lazy PresetService self) {
        this.self = self;
    }

    @Transactional(readOnly = true)
    public List<RagPresetDto> list(UUID userId) {
        List<RagPresetEntity> rows = new ArrayList<>(ragPresetRepository.findVisibleForUserWithProfileRefs(userId));
        // Keep product presets separate from the experimental catalog (P0–P14).
        // Experimental presets are surfaced through /lab/experimental-presets and shown in Chat as a separate section.
        rows.removeIf(PresetService::isExperimentalCatalogPreset);
        rows.sort(
                Comparator.comparing(RagPresetEntity::isSystem)
                        .thenComparing(RagPresetEntity::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return rows.stream().map(PresetService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<UserRagPreset> listUserPresets(UUID userId) {
        return list(userId).stream().map(PresetService::toUserRagPreset).toList();
    }

    @Transactional(readOnly = true)
    public RagPresetDto get(UUID userId, UUID presetId) {
        self.requireVisiblePreset(userId, presetId);
        RagPresetEntity e =
                ragPresetRepository.findByIdWithProfileRefs(presetId).orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Preset not found"));
        return toDto(e);
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
        e = ragPresetRepository.save(e);
        List<PresetProfileRefDto> refs = req.profileRefs() != null ? req.profileRefs() : List.of();
        validateAndReplaceProfileRefs(userId, e, refs);
        if (!refs.isEmpty()) {
            e.setCompositionVersion(1);
        }
        e = ragPresetRepository.save(e);
        auditApplicationService.persistAuditEntry(userId, "RAG_PRESET_CREATE", AUDIT_RESOURCE_TYPE_RAG_PRESET, e.getId(), Map.of("name", e.getName()));
        return toDto(refreshWithRefs(e.getId()));
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
        if (req.profileRefs() != null) {
            validateAndReplaceProfileRefs(userId, e, req.profileRefs());
            e.setCompositionVersion(e.getCompositionVersion() + 1);
        }
        e.setUpdatedAt(Instant.now());
        e = ragPresetRepository.save(e);
        auditApplicationService.persistAuditEntry(userId, "RAG_PRESET_UPDATE", AUDIT_RESOURCE_TYPE_RAG_PRESET, e.getId(), Map.of("name", e.getName()));
        return toDto(refreshWithRefs(e.getId()));
    }

    @Transactional
    public void delete(UUID userId, UUID presetId) {
        RagPresetEntity e = ragPresetRepository.findByIdAndOwner_Id(presetId, userId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Preset not found or not owned by user"));
        if (e.isSystem()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "System presets cannot be deleted");
        }
        UUID id = e.getId();
        ragPresetRepository.delete(e);
        auditApplicationService.persistAuditEntry(userId, "RAG_PRESET_DELETE", AUDIT_RESOURCE_TYPE_RAG_PRESET, id, Map.of());
    }

    /**
     * Copies sanitized preset values into the project's active PROJECT-level RAG configuration.
     */
    @Transactional
    public void applyInitialPresetToProject(UUID userId, UUID projectId, UUID presetId) {
        RagPresetEntity preset = self.requireVisiblePreset(userId, presetId);
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

    private RagPresetEntity refreshWithRefs(UUID presetId) {
        return ragPresetRepository.findByIdWithProfileRefs(presetId).orElseThrow();
    }

    private void validateAndReplaceProfileRefs(UUID userId, RagPresetEntity preset, List<PresetProfileRefDto> refs) {
        if (refs == null || refs.isEmpty()) {
            if (preset.getProfileRefs() != null) {
                preset.getProfileRefs().clear();
            }
            return;
        }
        Set<Integer> ordinals = new HashSet<>();
        for (PresetProfileRefDto r : refs) {
            if (!ordinals.add(r.ordinal())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate profile ref ordinal");
            }
        }
        List<PresetProfileRefDto> sorted = new ArrayList<>(refs);
        sorted.sort(Comparator.comparingInt(PresetProfileRefDto::ordinal));
        if (preset.getProfileRefs() == null) {
            preset.setProfileRefs(new ArrayList<>());
        } else {
            preset.getProfileRefs().clear();
        }
        for (PresetProfileRefDto r : sorted) {
            ConfigProfileEntity profile =
                    configProfileRepository
                            .findById(r.profileId())
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown profile"));
            if (profile.getOwner() != null && !profile.getOwner().getId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Profile not visible");
            }
            // System-scoped profiles (owner null) are visible to all users for preset composition.
            preset.getProfileRefs().add(RagPresetProfileRefEntity.link(preset, profile, r.ordinal(), r.role()));
        }
    }

    private static RagPresetDto toDto(RagPresetEntity e) {
        List<PresetProfileRefDto> profileRefs =
                e.getProfileRefs() == null || e.getProfileRefs().isEmpty()
                        ? List.of()
                        : e.getProfileRefs().stream()
                                .sorted(Comparator.comparingInt(RagPresetProfileRefEntity::getOrdinal))
                                .map(
                                        r ->
                                                new PresetProfileRefDto(
                                                        r.getOrdinal(),
                                                        r.getProfile().getId(),
                                                        r.getRole()))
                                .toList();
        return new RagPresetDto(
                e.getId(),
                e.getName(),
                e.getDescription(),
                e.getTags() != null ? List.copyOf(e.getTags()) : List.of(),
                e.getValues() != null ? Map.copyOf(e.getValues()) : Map.of(),
                e.isSystem(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                profileRefs);
    }

    private static boolean isExperimentalCatalogPreset(RagPresetEntity e) {
        if (e == null || e.getTags() == null) {
            return false;
        }
        return e.getTags().stream().anyMatch(t -> t != null && t.trim().equalsIgnoreCase("experimental"));
    }

    private static UserRagPreset toUserRagPreset(RagPresetDto dto) {
        return new UserRagPreset(
                dto.id(),
                dto.name(),
                dto.description(),
                dto.tags() != null ? List.copyOf(dto.tags()) : List.of(),
                dto.values() != null ? Map.copyOf(dto.values()) : Map.of(),
                dto.system(),
                dto.createdAt(),
                dto.updatedAt());
    }
}

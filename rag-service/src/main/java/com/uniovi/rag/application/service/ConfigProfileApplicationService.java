package com.uniovi.rag.application.service;

import com.uniovi.rag.application.port.ModelCatalogPort;
import com.uniovi.rag.domain.config.runtime.ConfigProfileType;
import com.uniovi.rag.infrastructure.persistence.ConfigProfileRepository;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ConfigProfileEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.interfaces.rest.dto.ConfigProfileResponseDto;
import com.uniovi.rag.interfaces.rest.dto.CreateConfigProfileRequest;
import com.uniovi.rag.interfaces.rest.dto.PatchConfigProfileRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
/**
 * CRUD for {@code config_profile} with governance checks and model allowlist validation.
 */
@Service
public class ConfigProfileApplicationService {

    private static final Set<String> MODEL_KEYS = Set.of("llmModel", "embeddingModel");

    private final ConfigProfileRepository configProfileRepository;
    private final UserRepository userRepository;
    private final ModelCatalogPort modelCatalogPort;
    private final AuditApplicationService auditApplicationService;

    public ConfigProfileApplicationService(
            ConfigProfileRepository configProfileRepository,
            UserRepository userRepository,
            ModelCatalogPort modelCatalogPort,
            AuditApplicationService auditApplicationService) {
        this.configProfileRepository = configProfileRepository;
        this.userRepository = userRepository;
        this.modelCatalogPort = modelCatalogPort;
        this.auditApplicationService = auditApplicationService;
    }

    @Transactional(readOnly = true)
    public List<ConfigProfileResponseDto> list(UUID userId) {
        return configProfileRepository.findVisibleForUser(userId).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ConfigProfileResponseDto get(UUID userId, UUID profileId) {
        ConfigProfileEntity e =
                configProfileRepository.findById(profileId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (e.getOwner() != null && !e.getOwner().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return toDto(e);
    }

    @Transactional
    public ConfigProfileResponseDto create(UUID userId, String roleName, CreateConfigProfileRequest req) {
        if (req.systemScope() && !"ADMIN".equalsIgnoreCase(roleName != null ? roleName : "")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admin can create system-scoped profiles");
        }
        ConfigProfileType type;
        try {
            type = ConfigProfileType.valueOf(req.profileType().trim());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown profileType");
        }
        validatePayloadModels(req.payload());
        UserEntity actor = userRepository.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        UserEntity owner = req.systemScope() ? null : actor;
        Instant now = Instant.now();
        ConfigProfileEntity entity =
                ConfigProfileEntity.newDraft(type, req.version(), req.label(), req.payload(), owner, actor, now);
        entity = configProfileRepository.save(entity);
        auditApplicationService.record(
                userId,
                "CONFIG_PROFILE_CREATE",
                "config_profile",
                entity.getId(),
                Map.of("profileType", type.name(), "systemScope", req.systemScope()));
        return toDto(entity);
    }

    @Transactional
    public ConfigProfileResponseDto patch(UUID userId, String roleName, UUID profileId, PatchConfigProfileRequest req) {
        if (req == null || (req.label() == null && req.payload() == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No fields to patch");
        }
        ConfigProfileEntity e =
                configProfileRepository.findById(profileId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (e.isImmutable()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Profile is immutable");
        }
        if (e.getOwner() == null && !"ADMIN".equalsIgnoreCase(roleName != null ? roleName : "")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admin can edit system-scoped profiles");
        }
        if (e.getOwner() != null && !e.getOwner().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (req.label() != null) {
            e.setLabel(req.label().isBlank() ? null : req.label().trim());
        }
        if (req.payload() != null) {
            validatePayloadModels(req.payload());
            e.setPayload(req.payload());
        }
        e = configProfileRepository.save(e);
        auditApplicationService.record(
                userId, "CONFIG_PROFILE_UPDATE", "config_profile", e.getId(), Map.of("patchedFields", req.nonNullFieldNames()));
        return toDto(e);
    }

    private void validatePayloadModels(Map<String, Object> payload) {
        Set<String> allowed = modelCatalogPort.allowedLlmNamesInGovernance();
        if (allowed.isEmpty()) {
            return;
        }
        for (String key : MODEL_KEYS) {
            Object v = payload.get(key);
            if (v == null) {
                continue;
            }
            String name = v.toString().trim();
            if (name.isEmpty()) {
                continue;
            }
            if (!allowed.contains(name)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Model '" + name + "' for key '" + key + "' is not in the allowlist");
            }
        }
    }

    private ConfigProfileResponseDto toDto(ConfigProfileEntity e) {
        return new ConfigProfileResponseDto(
                e.getId(),
                e.getProfileType().name(),
                e.getVersion(),
                e.getLabel(),
                e.getPayload(),
                e.getOwner() != null ? e.getOwner().getId() : null,
                e.isImmutable(),
                e.getCreatedAt());
    }
}

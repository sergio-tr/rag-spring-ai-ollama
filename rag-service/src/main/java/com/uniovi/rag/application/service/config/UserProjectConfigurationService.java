package com.uniovi.rag.application.service.config;

import com.uniovi.rag.domain.RagConfigurationLevel;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.RagConfigurationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.RagConfigurationEntityFactory;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.infrastructure.persistence.RagConfigurationRepository;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.application.service.project.ProjectAccessService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Persists and exposes effective RAG configuration at USER_DEFAULT and PROJECT levels.
 */
@Service
public class UserProjectConfigurationService {

    private final ObjectProvider<ConfigResolver> configResolverProvider;
    private final UserRepository userRepository;
    private final RagConfigurationRepository ragConfigurationRepository;
    private final ProjectAccessService projectAccessService;

    public UserProjectConfigurationService(
            ObjectProvider<ConfigResolver> configResolverProvider,
            UserRepository userRepository,
            RagConfigurationRepository ragConfigurationRepository,
            ProjectAccessService projectAccessService) {
        this.configResolverProvider = configResolverProvider;
        this.userRepository = userRepository;
        this.ragConfigurationRepository = ragConfigurationRepository;
        this.projectAccessService = projectAccessService;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getEffectiveUserConfig(UUID userId) {
        RagConfig c = configResolverProvider.getObject().resolve(userId, null, null);
        return c.toValueMap();
    }

    @Transactional
    public Map<String, Object> putUserConfig(UUID userId, Map<String, Object> body) {
        UserEntity user = userRepository.findById(userId).orElseThrow();
        Map<String, Object> sanitized = RagConfigValueSanitizer.sanitize(body);
        Optional<RagConfigurationEntity> existing =
                ragConfigurationRepository.findFirstByUser_IdAndLevelAndProjectIsNullAndActiveIsTrue(
                        userId, RagConfigurationLevel.USER_DEFAULT);
        Instant now = Instant.now();
        if (existing.isPresent()) {
            RagConfigurationEntity e = existing.get();
            e.setValues(sanitized);
            e.setUpdatedAt(now);
            ragConfigurationRepository.save(e);
        } else {
            ragConfigurationRepository.save(RagConfigurationEntityFactory.newUserDefault(user, sanitized, now));
        }
        return getEffectiveUserConfig(userId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getEffectiveProjectConfig(UUID userId, UUID projectId) {
        projectAccessService.requireOwnedProject(userId, projectId);
        RagConfig c = configResolverProvider.getObject().resolve(userId, projectId, null);
        return c.toValueMap();
    }

    @Transactional
    public Map<String, Object> putProjectConfig(UUID userId, UUID projectId, Map<String, Object> body) {
        ProjectEntity project = projectAccessService.requireOwnedProject(userId, projectId);
        UserEntity user = userRepository.findById(userId).orElseThrow();
        Map<String, Object> sanitized = RagConfigValueSanitizer.sanitize(body);
        Optional<RagConfigurationEntity> existing =
                ragConfigurationRepository.findFirstByUser_IdAndProject_IdAndLevelAndActiveIsTrue(
                        userId, projectId, RagConfigurationLevel.PROJECT);
        Instant now = Instant.now();
        if (existing.isPresent()) {
            RagConfigurationEntity e = existing.get();
            e.setValues(sanitized);
            e.setUpdatedAt(now);
            ragConfigurationRepository.save(e);
        } else {
            ragConfigurationRepository.save(
                    RagConfigurationEntityFactory.newProjectScoped(user, project, sanitized, now));
        }
        return getEffectiveProjectConfig(userId, projectId);
    }

    /**
     * Merges {@code patch} into the stored PROJECT-level RAG JSON (does not replace unrelated keys).
     */
    @Transactional
    public Map<String, Object> mergeProjectConfig(UUID userId, UUID projectId, Map<String, Object> patch) {
        ProjectEntity project = projectAccessService.requireOwnedProject(userId, projectId);
        UserEntity user = userRepository.findById(userId).orElseThrow();
        Map<String, Object> merged = new LinkedHashMap<>();
        Optional<RagConfigurationEntity> existing =
                ragConfigurationRepository.findFirstByUser_IdAndProject_IdAndLevelAndActiveIsTrue(
                        userId, projectId, RagConfigurationLevel.PROJECT);
        if (existing.isPresent()) {
            Map<String, Object> vals = existing.get().getValues();
            if (vals != null) {
                merged.putAll(vals);
            }
        }
        if (patch != null) {
            for (Map.Entry<String, Object> e : patch.entrySet()) {
                if (RagConfigValueSanitizer.ALLOWED_KEYS.contains(e.getKey())) {
                    merged.put(e.getKey(), e.getValue());
                }
            }
        }
        Map<String, Object> sanitized = RagConfigValueSanitizer.sanitize(merged);
        Instant now = Instant.now();
        if (existing.isPresent()) {
            RagConfigurationEntity e = existing.get();
            e.setValues(sanitized);
            e.setUpdatedAt(now);
            ragConfigurationRepository.save(e);
        } else {
            ragConfigurationRepository.save(
                    RagConfigurationEntityFactory.newProjectScoped(user, project, sanitized, now));
        }
        return getEffectiveProjectConfig(userId, projectId);
    }

    @Transactional
    public void deleteProjectConfig(UUID userId, UUID projectId) {
        projectAccessService.requireOwnedProject(userId, projectId);
        ragConfigurationRepository
                .findFirstByUser_IdAndProject_IdAndLevelAndActiveIsTrue(
                        userId, projectId, RagConfigurationLevel.PROJECT)
                .ifPresent(ragConfigurationRepository::delete);
    }
}

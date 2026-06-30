package com.uniovi.rag.application.service.config;

import com.uniovi.rag.application.config.PromptTemplateValidator;
import com.uniovi.rag.domain.RagConfigurationLevel;
import com.uniovi.rag.domain.config.prompt.PromptOverrideKeys;
import com.uniovi.rag.domain.llm.LlmConfigurationKeys;
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
    private final PromptTemplateValidator promptTemplateValidator;
    private final UserRepository userRepository;
    private final RagConfigurationRepository ragConfigurationRepository;
    private final ProjectAccessService projectAccessService;

    public UserProjectConfigurationService(
            ObjectProvider<ConfigResolver> configResolverProvider,
            PromptTemplateValidator promptTemplateValidator,
            UserRepository userRepository,
            RagConfigurationRepository ragConfigurationRepository,
            ProjectAccessService projectAccessService) {
        this.configResolverProvider = configResolverProvider;
        this.promptTemplateValidator = promptTemplateValidator;
        this.userRepository = userRepository;
        this.ragConfigurationRepository = ragConfigurationRepository;
        this.projectAccessService = projectAccessService;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getEffectiveUserConfig(UUID userId) {
        RagConfig c = configResolverProvider.getObject().resolve(userId, null, null);
        Map<String, Object> result = new LinkedHashMap<>(c.toValueMap());
        overlayStoredConfigurationKeys(
                result,
                ragConfigurationRepository
                        .findFirstByUser_IdAndLevelAndProjectIsNullAndActiveIsTrue(
                                userId, RagConfigurationLevel.USER_DEFAULT)
                        .map(RagConfigurationEntity::getValues));
        return result;
    }

    @Transactional
    public Map<String, Object> putUserConfig(UUID userId, Map<String, Object> body) {
        UserEntity user = userRepository.findById(userId).orElseThrow();
        Map<String, Object> sanitized = RagConfigValueSanitizer.sanitize(body);
        promptTemplateValidator.validateOverrides(sanitized);
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
        Map<String, Object> result = new LinkedHashMap<>(c.toValueMap());
        overlayStoredConfigurationKeys(
                result,
                ragConfigurationRepository
                        .findFirstByUser_IdAndProject_IdAndLevelAndActiveIsTrue(
                                userId, projectId, RagConfigurationLevel.PROJECT)
                        .map(RagConfigurationEntity::getValues));
        return result;
    }

    @Transactional
    public Map<String, Object> putProjectConfig(UUID userId, UUID projectId, Map<String, Object> body) {
        ProjectEntity project = projectAccessService.requireOwnedProject(userId, projectId);
        UserEntity user = userRepository.findById(userId).orElseThrow();
        Map<String, Object> sanitized = RagConfigValueSanitizer.sanitize(body);
        promptTemplateValidator.validateOverrides(sanitized);
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
                if (RagConfigValueSanitizer.isAllowedKey(e.getKey())) {
                    merged.put(e.getKey(), e.getValue());
                }
            }
        }
        Map<String, Object> sanitized = RagConfigValueSanitizer.sanitize(merged);
        promptTemplateValidator.validateOverrides(sanitized);
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

    private static void overlayStoredConfigurationKeys(
            Map<String, Object> target, Optional<Map<String, Object>> stored) {
        if (stored.isEmpty() || stored.get() == null) {
            return;
        }
        Map<String, Object> values = stored.get();
        copyIfPresent(target, values, LlmConfigurationKeys.SYSTEM_PROMPT);
        copyIfPresent(target, values, LlmConfigurationKeys.TEMPERATURE);
        copyIfPresent(target, values, LlmConfigurationKeys.ADDITIONAL_PARAMETERS);
        copyIfPresent(target, values, PromptOverrideKeys.OVERRIDES_MAP_KEY);
        copyIfPresent(target, values, PromptOverrideKeys.TASK_LLM_OVERRIDES_MAP_KEY);
        for (Map.Entry<String, Object> e : values.entrySet()) {
            if (PromptOverrideKeys.isPromptOverrideKey(e.getKey())) {
                target.put(e.getKey(), e.getValue());
            }
        }
    }

    private static void copyIfPresent(
            Map<String, Object> target, Map<String, Object> source, String key) {
        if (source.containsKey(key)) {
            target.put(key, source.get(key));
        }
    }
}

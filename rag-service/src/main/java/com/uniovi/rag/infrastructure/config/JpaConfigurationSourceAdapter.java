package com.uniovi.rag.infrastructure.config;

import com.uniovi.rag.application.port.ConfigurationSourcePort;
import com.uniovi.rag.domain.RagConfigurationLevel;
import com.uniovi.rag.infrastructure.persistence.DefaultSystemConfigurationRepository;
import com.uniovi.rag.infrastructure.persistence.RagConfigurationRepository;
import com.uniovi.rag.infrastructure.persistence.UserPersonalizationRepository;
import com.uniovi.rag.infrastructure.persistence.UserPreferencesRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.RagConfigurationEntity;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Loads persisted configuration JSON maps for the cascade (adapter for {@link ConfigurationSourcePort}).
 * User-default layer performs dual read: {@code rag_configuration} USER_DEFAULT merged with
 * {@code user_preferences} and {@code user_personalization} (later maps overwrite keys).
 */
@Service
public class JpaConfigurationSourceAdapter implements ConfigurationSourcePort {

    private final DefaultSystemConfigurationRepository defaultSystemRepository;
    private final RagConfigurationRepository ragConfigurationRepository;
    private final UserPreferencesRepository userPreferencesRepository;
    private final UserPersonalizationRepository userPersonalizationRepository;

    public JpaConfigurationSourceAdapter(
            DefaultSystemConfigurationRepository defaultSystemRepository,
            RagConfigurationRepository ragConfigurationRepository,
            UserPreferencesRepository userPreferencesRepository,
            UserPersonalizationRepository userPersonalizationRepository) {
        this.defaultSystemRepository = defaultSystemRepository;
        this.ragConfigurationRepository = ragConfigurationRepository;
        this.userPreferencesRepository = userPreferencesRepository;
        this.userPersonalizationRepository = userPersonalizationRepository;
    }

    @Override
    public Optional<Map<String, Object>> loadSystemDefaults() {
        return defaultSystemRepository
                .findFirstByOrderByUpdatedAtDesc()
                .map(row -> row.getValues() != null ? row.getValues() : Map.<String, Object>of());
    }

    @Override
    public Optional<Map<String, Object>> loadUserDefault(UUID userId) {
        Optional<Map<String, Object>> legacy =
                ragConfigurationRepository
                        .findFirstByUser_IdAndLevelAndProjectIsNullAndActiveIsTrue(userId, RagConfigurationLevel.USER_DEFAULT)
                        .map(RagConfigurationEntity::getValues);
        Optional<Map<String, Object>> prefs =
                userPreferencesRepository.findById(userId).map(p -> p.getPreferences() != null ? p.getPreferences() : Map.<String, Object>of());
        Optional<Map<String, Object>> pers =
                userPersonalizationRepository
                        .findById(userId)
                        .map(p -> p.getPersonalization() != null ? p.getPersonalization() : Map.<String, Object>of());
        if (legacy.isEmpty() && prefs.map(Map::isEmpty).orElse(true) && pers.map(Map::isEmpty).orElse(true)) {
            return Optional.empty();
        }
        Map<String, Object> merged = new LinkedHashMap<>();
        legacy.ifPresent(merged::putAll);
        prefs.ifPresent(merged::putAll);
        pers.ifPresent(merged::putAll);
        return Optional.of(merged);
    }

    @Override
    public Optional<Map<String, Object>> loadProject(UUID userId, UUID projectId) {
        return ragConfigurationRepository
                .findFirstByUser_IdAndProject_IdAndLevelAndActiveIsTrue(
                        userId, projectId, RagConfigurationLevel.PROJECT)
                .map(RagConfigurationEntity::getValues);
    }
}

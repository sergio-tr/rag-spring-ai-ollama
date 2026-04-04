package com.uniovi.rag.infrastructure.config;

import com.uniovi.rag.application.port.ConfigurationSourcePort;
import com.uniovi.rag.domain.RagConfigurationLevel;
import com.uniovi.rag.infrastructure.persistence.DefaultSystemConfigurationRepository;
import com.uniovi.rag.infrastructure.persistence.RagConfigurationRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.RagConfigurationEntity;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Loads persisted configuration JSON maps for the cascade (adapter for {@link ConfigurationSourcePort}).
 */
@Service
public class JpaConfigurationSourceAdapter implements ConfigurationSourcePort {

    private final DefaultSystemConfigurationRepository defaultSystemRepository;
    private final RagConfigurationRepository ragConfigurationRepository;

    public JpaConfigurationSourceAdapter(
            DefaultSystemConfigurationRepository defaultSystemRepository,
            RagConfigurationRepository ragConfigurationRepository) {
        this.defaultSystemRepository = defaultSystemRepository;
        this.ragConfigurationRepository = ragConfigurationRepository;
    }

    @Override
    public Optional<Map<String, Object>> loadSystemDefaults() {
        return defaultSystemRepository
                .findFirstByOrderByUpdatedAtDesc()
                .map(row -> row.getValues() != null ? row.getValues() : Map.<String, Object>of());
    }

    @Override
    public Optional<Map<String, Object>> loadUserDefault(UUID userId) {
        return ragConfigurationRepository
                .findFirstByUser_IdAndLevelAndProjectIsNullAndActiveIsTrue(userId, RagConfigurationLevel.USER_DEFAULT)
                .map(RagConfigurationEntity::getValues);
    }

    @Override
    public Optional<Map<String, Object>> loadProject(UUID userId, UUID projectId) {
        return ragConfigurationRepository
                .findFirstByUser_IdAndProject_IdAndLevelAndActiveIsTrue(
                        userId, projectId, RagConfigurationLevel.PROJECT)
                .map(RagConfigurationEntity::getValues);
    }
}

package com.uniovi.rag.service.admin;

import com.uniovi.rag.infrastructure.persistence.DefaultSystemConfigurationRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.DefaultSystemConfigurationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.DefaultSystemConfigurationEntityFactory;
import com.uniovi.rag.service.config.RagConfigValueSanitizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Read/write {@code default_system_configuration} (new-user defaults; admin-only API).
 */
@Service
public class AdminSystemDefaultsService {

    private final DefaultSystemConfigurationRepository defaultSystemConfigurationRepository;

    public AdminSystemDefaultsService(DefaultSystemConfigurationRepository defaultSystemConfigurationRepository) {
        this.defaultSystemConfigurationRepository = defaultSystemConfigurationRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDefaults() {
        Optional<DefaultSystemConfigurationEntity> row =
                defaultSystemConfigurationRepository.findFirstByOrderByUpdatedAtDesc();
        return row.map(e -> e.getValues() != null ? e.getValues() : Map.<String, Object>of())
                .orElse(Map.of());
    }

    @Transactional
    public Map<String, Object> putDefaults(Map<String, Object> body) {
        Map<String, Object> sanitized = RagConfigValueSanitizer.sanitize(body);
        Instant now = Instant.now();
        DefaultSystemConfigurationEntity e =
                defaultSystemConfigurationRepository
                        .findFirstByOrderByUpdatedAtDesc()
                        .orElseGet(
                                () ->
                                        defaultSystemConfigurationRepository.save(
                                                DefaultSystemConfigurationEntityFactory.emptyRow()));
        e.setValues(sanitized);
        e.setUpdatedAt(now);
        defaultSystemConfigurationRepository.save(e);
        return getDefaults();
    }
}

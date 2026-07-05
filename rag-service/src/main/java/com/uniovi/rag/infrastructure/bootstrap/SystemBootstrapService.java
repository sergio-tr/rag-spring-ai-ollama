package com.uniovi.rag.infrastructure.bootstrap;

import com.uniovi.rag.domain.llm.TaskLlmRoleDefaultsSeeder;
import com.uniovi.rag.infrastructure.persistence.jpa.DefaultSystemConfigurationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.DefaultSystemConfigurationEntityFactory;
import com.uniovi.rag.infrastructure.persistence.DefaultSystemConfigurationRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ensures singleton system configuration exists after migrations (idempotent).
 * Seeds per-role task LLM defaults when missing.
 */
@Component
@Order(100)
public class SystemBootstrapService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SystemBootstrapService.class);

    private final DefaultSystemConfigurationRepository defaultSystemConfigurationRepository;

    public SystemBootstrapService(DefaultSystemConfigurationRepository defaultSystemConfigurationRepository) {
        this.defaultSystemConfigurationRepository = defaultSystemConfigurationRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        DefaultSystemConfigurationEntity row =
                defaultSystemConfigurationRepository
                        .findFirstByOrderByUpdatedAtDesc()
                        .orElseGet(
                                () -> {
                                    DefaultSystemConfigurationEntity created =
                                            DefaultSystemConfigurationEntityFactory.emptyRow();
                                    defaultSystemConfigurationRepository.save(created);
                                    log.info("Bootstrap: created default_system_configuration row");
                                    return created;
                                });
        Map<String, Object> values =
                row.getValues() != null ? new LinkedHashMap<>(row.getValues()) : new LinkedHashMap<>();
        if (!TaskLlmRoleDefaultsSeeder.hasCompleteTaskLlmDefaults(values)) {
            row.setValues(TaskLlmRoleDefaultsSeeder.mergeMissingSystemDefaults(values));
            defaultSystemConfigurationRepository.save(row);
            log.info("Bootstrap: seeded system taskLlmOverrides role defaults");
        }
    }
}

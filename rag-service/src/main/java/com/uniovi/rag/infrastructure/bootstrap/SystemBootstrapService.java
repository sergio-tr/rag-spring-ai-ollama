package com.uniovi.rag.infrastructure.bootstrap;

import com.uniovi.rag.infrastructure.persistence.jpa.DefaultSystemConfigurationEntityFactory;
import com.uniovi.rag.infrastructure.persistence.DefaultSystemConfigurationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ensures singleton system configuration exists after migrations (idempotent).
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
        if (defaultSystemConfigurationRepository.findFirstByOrderByUpdatedAtDesc().isEmpty()) {
            defaultSystemConfigurationRepository.save(DefaultSystemConfigurationEntityFactory.emptyRow());
            log.info("Bootstrap: created default_system_configuration row");
        }
    }
}

package com.uniovi.rag.ollama;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Runs Ollama model provisioning as early as possible after the application context starts.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OllamaProvisioningApplicationRunner implements ApplicationRunner {

    private final OllamaModelProvisioningService provisioningService;

    public OllamaProvisioningApplicationRunner(OllamaModelProvisioningService provisioningService) {
        this.provisioningService = provisioningService;
    }

    @Override
    public void run(ApplicationArguments args) {
        provisioningService.ensureConfiguredModelsAtStartup();
    }
}

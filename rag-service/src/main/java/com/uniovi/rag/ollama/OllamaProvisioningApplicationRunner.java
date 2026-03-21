package com.uniovi.rag.ollama;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Ejecuta el aprovisionamiento de modelos Ollama lo antes posible tras el arranque del contexto.
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

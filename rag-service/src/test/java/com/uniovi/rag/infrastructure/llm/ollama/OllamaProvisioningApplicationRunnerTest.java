package com.uniovi.rag.infrastructure.llm.ollama;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OllamaProvisioningApplicationRunnerTest {

    @Test
    void run_delegatesToProvisioningService() {
        OllamaModelProvisioningService svc = mock(OllamaModelProvisioningService.class);
        OllamaProvisioningApplicationRunner runner = new OllamaProvisioningApplicationRunner(svc);
        runner.run(new DefaultApplicationArguments());
        verify(svc).ensureConfiguredModelsAtStartup();
    }
}

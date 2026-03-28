package com.uniovi.rag.ollama;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.mockito.Mockito.*;

class OllamaProvisioningApplicationRunnerTest {

    @Test
    void run_delegatesToProvisioningService() {
        OllamaModelProvisioningService svc = mock(OllamaModelProvisioningService.class);
        OllamaProvisioningApplicationRunner runner = new OllamaProvisioningApplicationRunner(svc);
        runner.run(new DefaultApplicationArguments());
        verify(svc).ensureConfiguredModelsAtStartup();
    }
}

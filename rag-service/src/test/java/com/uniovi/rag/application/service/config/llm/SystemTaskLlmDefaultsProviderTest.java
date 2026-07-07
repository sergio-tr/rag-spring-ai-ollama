package com.uniovi.rag.application.service.config.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.port.ConfigurationSourcePort;
import com.uniovi.rag.domain.config.prompt.PromptOverrideKeys;
import com.uniovi.rag.domain.llm.TaskLlmRoleDefaults;
import com.uniovi.rag.domain.llm.TaskLlmTask;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SystemTaskLlmDefaultsProviderTest {

    @Mock private ConfigurationSourcePort configurationSource;

    private SystemTaskLlmDefaultsProvider provider;

    @BeforeEach
    void setUp() {
        provider = new SystemTaskLlmDefaultsProvider(configurationSource);
    }

    @Test
    void baselineFor_readsModelFromSystemDb() {
        when(configurationSource.loadSystemDefaults())
                .thenReturn(
                        Optional.of(
                                Map.of(
                                        PromptOverrideKeys.TASK_LLM_OVERRIDES_MAP_KEY,
                                        Map.of(
                                                TaskLlmTask.QUERY_REWRITE.id(),
                                                Map.of(
                                                        "enabled",
                                                        true,
                                                        "model",
                                                        "db-qwen",
                                                        "temperature",
                                                        0.3)))));

        TaskLlmRoleDefaults.RoleDefault baseline = provider.baselineFor(TaskLlmTask.QUERY_REWRITE);

        assertThat(baseline.modelId()).isEqualTo("db-qwen");
        assertThat(baseline.parameters().temperature()).isEqualTo(0.3);
    }

    @Test
    void baselineFor_fallsBackToSeedWhenDbMissing() {
        when(configurationSource.loadSystemDefaults()).thenReturn(Optional.empty());

        TaskLlmRoleDefaults.RoleDefault baseline = provider.baselineFor(TaskLlmTask.LLM_RANKER);

        assertThat(baseline.modelId()).isEqualTo(TaskLlmRoleDefaults.forTask(TaskLlmTask.LLM_RANKER).modelId());
    }
}

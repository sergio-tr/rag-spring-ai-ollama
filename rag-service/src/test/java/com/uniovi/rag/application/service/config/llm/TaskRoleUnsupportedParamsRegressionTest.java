package com.uniovi.rag.application.service.config.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.application.service.llm.LlmProviderParameterFilter;
import com.uniovi.rag.application.service.llm.catalog.LlmModelCatalogService;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.TaskLlmGenerationParameters;
import com.uniovi.rag.domain.llm.TaskLlmRoleDefaults;
import com.uniovi.rag.domain.llm.TaskLlmTask;
import com.uniovi.rag.infrastructure.llm.LlmOpenAiCompatibleDefaults;
import com.uniovi.rag.infrastructure.llm.LlmOllamaDefaults;
import com.uniovi.rag.infrastructure.llm.LlmProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TaskRoleUnsupportedParamsRegressionTest {

    private LlmProviderParameterFilter parameterFilter;

    @BeforeEach
    void setUp() {
        LlmProperties properties = new LlmProperties();
        LlmOllamaDefaults ollama = properties.getOllama();
        ollama.setDefaultChatModel("qwen3.5:9b");
        ollama.setAvailableChatModels(List.of("qwen3.5:9b", "gemma4:12b"));
        LlmOpenAiCompatibleDefaults openAi = properties.getOpenAiCompatible();
        openAi.setDefaultBaseUrl("http://litellm:4000");
        openAi.setAvailableChatModels(List.of("qwen3.5:9b", "gemma4:12b", "gpt-oss:20b"));
        parameterFilter = new LlmProviderParameterFilter(new LlmModelCatalogService(properties));
    }

    @ParameterizedTest
    @EnumSource(TaskLlmTask.class)
    void taskRoleDefaults_keepEffectivePenaltiesButOmitFromOllamaPayload(TaskLlmTask task) {
        TaskLlmGenerationParameters effective = TaskLlmRoleDefaults.forTask(task).parameters();

        assertThat(effective.presencePenalty()).isNotNull();
        assertThat(effective.frequencyPenalty()).isNotNull();

        var filtered =
                parameterFilter.filterAdditionalParameters(
                        LlmProvider.OPENAI_COMPATIBLE,
                        TaskLlmRoleDefaults.forTask(task).modelId(),
                        effective.toAdditionalParameters());

        assertThat(filtered).doesNotContainKeys("presencePenalty", "frequencyPenalty", "presence_penalty", "frequency_penalty");
        if (TaskLlmRoleDefaults.forTask(task).modelId().contains("qwen3")
                || TaskLlmRoleDefaults.forTask(task).modelId().contains("gpt-oss")) {
            assertThat(filtered).containsEntry("think", Boolean.FALSE);
        }
    }
}

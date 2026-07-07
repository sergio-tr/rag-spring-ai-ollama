package com.uniovi.rag.application.service.config.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.llm.TaskLlmTask;
import org.junit.jupiter.api.Test;

class TaskModelSettingsDefaultsTest {

    @Test
    void everyRole_hasBuiltInDefaults() {
        for (TaskLlmTask task : TaskLlmTask.catalogTasks()) {
            var dto = TaskModelSettingsService.displayDefault(task);
            assertThat(dto.role()).isEqualTo(task.name());
            assertThat(dto.modelId()).isNotBlank();
            assertThat(dto.parameters()).isNotEmpty();
            assertThat(dto.parameters()).containsKey("temperature");
            assertThat(dto.parameters()).containsKey("topP");
            assertThat(dto.parameters()).containsKey("maxTokens");
            assertThat(dto.parameters()).containsKey("responseFormat");
            assertThat(dto.parameters().get("think")).isEqualTo(false);
        }
    }

    @Test
    void finalAnswer_defaultModel_isNotCodellama() {
        var dto = TaskModelSettingsService.displayDefault(TaskLlmTask.FINAL_ANSWER);
        assertThat(dto.modelId()).isEqualTo("qwen3.5:9b");
        assertThat(dto.modelId()).doesNotContain("codellama");
    }

    @Test
    void runtimeJudge_defaultsToJsonObjectResponseFormat() {
        var dto = TaskModelSettingsService.displayDefault(TaskLlmTask.RUNTIME_JUDGE);
        assertThat(dto.parameters().get("responseFormat")).isEqualTo("json_object");
    }
}

package com.uniovi.rag.infrastructure.observability;

import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AsyncTaskObservabilityTest {

    @Test
    void subsystem_mapsAllKnownTypes() {
        assertThat(AsyncTaskObservability.subsystem(AsyncTaskType.CHAT_MESSAGE)).isEqualTo("product");
        assertThat(AsyncTaskObservability.subsystem(AsyncTaskType.EVAL_LLM)).isEqualTo("lab");
        assertThat(AsyncTaskObservability.subsystem(AsyncTaskType.EVAL_RAG)).isEqualTo("lab");
        assertThat(AsyncTaskObservability.subsystem(AsyncTaskType.EVAL_EMBEDDING_RETRIEVAL)).isEqualTo("lab");
        assertThat(AsyncTaskObservability.subsystem(AsyncTaskType.CLASSIFIER_TRAIN)).isEqualTo("classifier");
        assertThat(AsyncTaskObservability.subsystem(AsyncTaskType.CLASSIFIER_EVAL)).isEqualTo("classifier");
        assertThat(AsyncTaskObservability.subsystem(AsyncTaskType.OLLAMA_PULL)).isEqualTo("admin");
        assertThat(AsyncTaskObservability.subsystem(AsyncTaskType.ACCOUNT_EXPORT)).isEqualTo("account");
        assertThat(AsyncTaskObservability.subsystem(AsyncTaskType.ACCOUNT_DELETION)).isEqualTo("account");
    }

    @Test
    void spanAttributes_includesExpectedKeys_andOmitsNullOptionals() {
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        AsyncTaskEntity task = mock(AsyncTaskEntity.class);
        when(task.getId()).thenReturn(taskId);
        when(task.getTaskType()).thenReturn(AsyncTaskType.ACCOUNT_EXPORT);

        UserEntity user = mock(UserEntity.class);
        when(user.getId()).thenReturn(userId);
        when(task.getUser()).thenReturn(user);

        ProjectEntity project = mock(ProjectEntity.class);
        when(project.getId()).thenReturn(projectId);
        when(task.getProject()).thenReturn(project);

        Map<String, String> attrs = AsyncTaskObservability.spanAttributes(task);
        assertThat(attrs)
                .containsEntry("rag.task_id", taskId.toString())
                .containsEntry("rag.task_type", AsyncTaskType.ACCOUNT_EXPORT.name())
                .containsEntry("rag.subsystem", "account")
                .containsEntry("rag.user_id", userId.toString())
                .containsEntry("rag.project_id", projectId.toString());
    }

    @Test
    void spanAttributes_doesNotIncludeUserOrProjectWhenMissing() {
        UUID taskId = UUID.randomUUID();

        AsyncTaskEntity task = mock(AsyncTaskEntity.class);
        when(task.getId()).thenReturn(taskId);
        when(task.getTaskType()).thenReturn(AsyncTaskType.CHAT_MESSAGE);
        when(task.getUser()).thenReturn(null);
        when(task.getProject()).thenReturn(null);

        Map<String, String> attrs = AsyncTaskObservability.spanAttributes(task);
        assertThat(attrs)
                .containsEntry("rag.task_id", taskId.toString())
                .containsEntry("rag.task_type", AsyncTaskType.CHAT_MESSAGE.name())
                .containsEntry("rag.subsystem", "product");
        assertThat(attrs).doesNotContainKeys("rag.user_id", "rag.project_id");
    }
}


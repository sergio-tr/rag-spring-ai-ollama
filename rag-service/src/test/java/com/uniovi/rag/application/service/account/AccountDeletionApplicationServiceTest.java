package com.uniovi.rag.application.service.account;

import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.application.service.async.AsyncTaskMutationService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountDeletionApplicationServiceTest {

    @Mock
    private AccountDeletionOrchestrator accountDeletionOrchestrator;

    @Mock
    private AsyncTaskMutationService mutation;

    @InjectMocks
    private AccountDeletionApplicationService service;

    @Test
    void runDeletion_delegatesOrchestrator_thenMarksSucceeded() {
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UserEntity user = Mockito.mock(UserEntity.class);
        when(user.getId()).thenReturn(userId);
        AsyncTaskEntity task = Mockito.mock(AsyncTaskEntity.class);
        when(task.getId()).thenReturn(taskId);
        when(task.getUser()).thenReturn(user);

        service.runDeletion(task, mutation);

        verify(accountDeletionOrchestrator).deleteUserAccount(userId);
        verify(mutation)
                .markSucceeded(
                        eq(taskId),
                        eq(Map.of(AccountJobPayloadKeys.TASK_TYPE, "ACCOUNT_DELETION", "deleted", Boolean.TRUE)));
    }

    @Test
    void runDeletion_marksFailed_whenOrchestratorThrows() {
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UserEntity user = Mockito.mock(UserEntity.class);
        when(user.getId()).thenReturn(userId);
        AsyncTaskEntity task = Mockito.mock(AsyncTaskEntity.class);
        when(task.getId()).thenReturn(taskId);
        when(task.getUser()).thenReturn(user);
        doThrow(new IllegalStateException("db blocked")).when(accountDeletionOrchestrator).deleteUserAccount(userId);

        assertThatThrownBy(() -> service.runDeletion(task, mutation)).isInstanceOf(IllegalStateException.class);

        verify(mutation).markFailed(eq(taskId), eq("db blocked"));
    }
}

package com.uniovi.rag.service.async.account;

import com.uniovi.rag.application.service.account.AccountDeletionApplicationService;
import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.service.async.AsyncTaskMutationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountDeletionJobHandlerTest {

    @Mock
    private AccountDeletionApplicationService accountDeletionApplicationService;

    @Mock
    private AsyncTaskMutationService mutation;

    @Test
    void taskType_isAccountDeletion() {
        assertThat(new AccountDeletionJobHandler(accountDeletionApplicationService).taskType())
                .isEqualTo(AsyncTaskType.ACCOUNT_DELETION);
    }

    @Test
    void run_delegatesToApplicationService() {
        UUID taskId = UUID.randomUUID();
        AsyncTaskEntity task = org.mockito.Mockito.mock(AsyncTaskEntity.class);
        when(task.getId()).thenReturn(taskId);

        new AccountDeletionJobHandler(accountDeletionApplicationService).run(task, mutation);

        verify(accountDeletionApplicationService).runDeletion(task, mutation);
    }

    @Test
    void run_marksFailed_withClassName_whenMessageNull() {
        UUID taskId = UUID.randomUUID();
        AsyncTaskEntity task = org.mockito.Mockito.mock(AsyncTaskEntity.class);
        when(task.getId()).thenReturn(taskId);
        doThrow(new NullPointerException()).when(accountDeletionApplicationService).runDeletion(task, mutation);

        new AccountDeletionJobHandler(accountDeletionApplicationService).run(task, mutation);

        verify(mutation).markFailed(eq(taskId), eq("NullPointerException"));
    }
}

package com.uniovi.rag.service.async.account;

import com.uniovi.rag.application.service.account.AccountExportApplicationService;
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
class AccountExportJobHandlerTest {

    @Mock
    private AccountExportApplicationService accountExportApplicationService;

    @Mock
    private AsyncTaskMutationService mutation;

    @Test
    void taskType_isAccountExport() {
        assertThat(new AccountExportJobHandler(accountExportApplicationService).taskType())
                .isEqualTo(AsyncTaskType.ACCOUNT_EXPORT);
    }

    @Test
    void run_delegatesToApplicationService() throws Exception {
        UUID taskId = UUID.randomUUID();
        AsyncTaskEntity task = org.mockito.Mockito.mock(AsyncTaskEntity.class);
        when(task.getId()).thenReturn(taskId);

        new AccountExportJobHandler(accountExportApplicationService).run(task, mutation);

        verify(accountExportApplicationService).runExport(task, mutation);
    }

    @Test
    void run_marksFailed_whenExportThrows() throws Exception {
        UUID taskId = UUID.randomUUID();
        AsyncTaskEntity task = org.mockito.Mockito.mock(AsyncTaskEntity.class);
        when(task.getId()).thenReturn(taskId);
        doThrow(new RuntimeException("disk full")).when(accountExportApplicationService).runExport(task, mutation);

        new AccountExportJobHandler(accountExportApplicationService).run(task, mutation);

        verify(mutation).markFailed(eq(taskId), eq("disk full"));
    }
}

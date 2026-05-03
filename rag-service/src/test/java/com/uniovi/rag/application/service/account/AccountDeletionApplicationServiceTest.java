package com.uniovi.rag.application.service.account;

import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.service.async.AsyncTaskMutationService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountDeletionApplicationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AsyncTaskMutationService mutation;

    @InjectMocks
    private AccountDeletionApplicationService service;

    @Test
    void runDeletion_marksSucceeded_thenDeletesUser() {
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UserEntity user = Mockito.mock(UserEntity.class);
        when(user.getId()).thenReturn(userId);
        AsyncTaskEntity task = Mockito.mock(AsyncTaskEntity.class);
        when(task.getId()).thenReturn(taskId);
        when(task.getUser()).thenReturn(user);

        service.runDeletion(task, mutation);

        ArgumentCaptor<Map<String, Object>> res = ArgumentCaptor.forClass(Map.class);
        verify(mutation).markSucceeded(eq(taskId), res.capture());
        assertThat(res.getValue())
                .containsEntry(AccountJobPayloadKeys.TASK_TYPE, "ACCOUNT_DELETION")
                .containsEntry("deleted", Boolean.TRUE);
        verify(userRepository).deleteById(userId);
    }
}

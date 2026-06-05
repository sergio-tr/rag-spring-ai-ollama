package com.uniovi.rag.application.service.account;

import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.application.service.async.AsyncTaskMutationService;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Async account deletion entry point; delegates ordered purge to {@link AccountDeletionOrchestrator}.
 */
@Service
public class AccountDeletionApplicationService {

    private final AccountDeletionOrchestrator accountDeletionOrchestrator;

    public AccountDeletionApplicationService(AccountDeletionOrchestrator accountDeletionOrchestrator) {
        this.accountDeletionOrchestrator = accountDeletionOrchestrator;
    }

    @Transactional
    public void runDeletion(AsyncTaskEntity task, AsyncTaskMutationService mutation) {
        UUID taskId = task.getId();
        UUID userId = task.getUser().getId();
        try {
            accountDeletionOrchestrator.deleteUserAccount(userId);
            mutation.markSucceeded(
                    taskId,
                    Map.of(AccountJobPayloadKeys.TASK_TYPE, "ACCOUNT_DELETION", "deleted", Boolean.TRUE));
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            mutation.markFailed(taskId, msg);
            throw e;
        }
    }
}

package com.uniovi.rag.service.async.account;

import com.uniovi.rag.application.service.account.AccountDeletionApplicationService;
import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.service.async.AsyncTaskMutationService;
import com.uniovi.rag.service.async.lab.LabJobHandler;
import org.springframework.stereotype.Component;

@Component
class AccountDeletionJobHandler implements LabJobHandler {

    private final AccountDeletionApplicationService accountDeletionApplicationService;

    AccountDeletionJobHandler(AccountDeletionApplicationService accountDeletionApplicationService) {
        this.accountDeletionApplicationService = accountDeletionApplicationService;
    }

    @Override
    public AsyncTaskType taskType() {
        return AsyncTaskType.ACCOUNT_DELETION;
    }

    @Override
    public void run(AsyncTaskEntity task, AsyncTaskMutationService mutation) {
        try {
            accountDeletionApplicationService.runDeletion(task, mutation);
        } catch (Exception e) {
            mutation.markFailed(task.getId(), e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }
}

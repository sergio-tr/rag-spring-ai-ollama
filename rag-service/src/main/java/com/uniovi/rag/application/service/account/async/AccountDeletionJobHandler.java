package com.uniovi.rag.application.service.account.async;

import com.uniovi.rag.application.service.account.AccountDeletionApplicationService;
import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.application.service.async.AsyncTaskMutationService;
import com.uniovi.rag.application.service.evaluation.async.LabJobHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class AccountDeletionJobHandler implements LabJobHandler {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionJobHandler.class);

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
        log.info(
                "account_deletion_job_start taskId={} userId={}",
                task.getId(),
                task.getUser() != null ? task.getUser().getId() : null);
        try {
            accountDeletionApplicationService.runDeletion(task, mutation);
        } catch (Exception e) {
            mutation.markFailed(task.getId(), e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }
}

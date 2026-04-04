package com.uniovi.rag.service.async.account;

import com.uniovi.rag.application.service.account.AccountExportApplicationService;
import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.service.async.AsyncTaskMutationService;
import com.uniovi.rag.service.async.lab.LabJobHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class AccountExportJobHandler implements LabJobHandler {

    private static final Logger log = LoggerFactory.getLogger(AccountExportJobHandler.class);

    private final AccountExportApplicationService accountExportApplicationService;

    AccountExportJobHandler(AccountExportApplicationService accountExportApplicationService) {
        this.accountExportApplicationService = accountExportApplicationService;
    }

    @Override
    public AsyncTaskType taskType() {
        return AsyncTaskType.ACCOUNT_EXPORT;
    }

    @Override
    public void run(AsyncTaskEntity task, AsyncTaskMutationService mutation) {
        log.info(
                "account_export_job_start taskId={} userId={}",
                task.getId(),
                task.getUser() != null ? task.getUser().getId() : null);
        try {
            accountExportApplicationService.runExport(task, mutation);
        } catch (Exception e) {
            mutation.markFailed(task.getId(), e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }
}

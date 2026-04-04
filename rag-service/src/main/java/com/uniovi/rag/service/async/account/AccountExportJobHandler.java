package com.uniovi.rag.service.async.account;

import com.uniovi.rag.application.service.account.AccountExportApplicationService;
import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.service.async.AsyncTaskMutationService;
import com.uniovi.rag.service.async.lab.LabJobHandler;
import org.springframework.stereotype.Component;

import java.security.NoSuchAlgorithmException;

@Component
class AccountExportJobHandler implements LabJobHandler {

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
        try {
            accountExportApplicationService.runExport(task, mutation);
        } catch (Exception e) {
            mutation.markFailed(task.getId(), e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }
}

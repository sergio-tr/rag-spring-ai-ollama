package com.uniovi.rag.infrastructure.transaction;

import com.uniovi.rag.application.port.AfterCommitTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runs tasks after successful commit of the surrounding Spring transaction.
 */
@Component
public class SpringAfterCommitTaskScheduler implements AfterCommitTaskScheduler {

    @Override
    public void scheduleAfterCommit(Runnable task) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        task.run();
                    }
                });
    }
}

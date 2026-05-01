package com.uniovi.rag.application.port;

/**
 * Schedules work to run after the current transaction commits (production) or immediately in unit tests.
 */
@FunctionalInterface
public interface AfterCommitTaskScheduler {

    void scheduleAfterCommit(Runnable task);
}

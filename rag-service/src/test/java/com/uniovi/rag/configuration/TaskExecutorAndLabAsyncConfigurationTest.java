package com.uniovi.rag.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskExecutorAndLabAsyncConfigurationTest {

    @Test
    void taskExecutorUsesRagThreadPrefix() {
        TaskExecutor exec = new TaskExecutorConfiguration().taskExecutor();
        assertInstanceOf(ThreadPoolTaskExecutor.class, exec);
        assertTrue(((ThreadPoolTaskExecutor) exec).getThreadNamePrefix().contains("rag-"));
    }

    @Test
    void labExecutorsStartAndStopCleanly() {
        LabAsyncConfiguration cfg = new LabAsyncConfiguration();
        Executor lab = cfg.labExecutor();
        assertInstanceOf(ThreadPoolTaskExecutor.class, lab);
        ScheduledExecutorService sse = cfg.labJobSseExecutor();
        assertEquals(0, sse.shutdownNow().size());
        ((ThreadPoolTaskExecutor) lab).shutdown();
    }
}

package com.uniovi.rag.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Thread pool for long-running lab/admin background work ({@code @Async("labExecutor")}) and SSE polling.
 */
@Configuration
public class LabAsyncConfiguration {

    @Bean(name = "labExecutor")
    public Executor labExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(4);
        ex.setQueueCapacity(50);
        ex.setThreadNamePrefix("lab-async-");
        ex.initialize();
        return ex;
    }

    /**
     * Dedicated executor for document ingestion pipelines.
     *
     * <p>Rationale: uploads can arrive in bursts (multi-file drag & drop) and should not starve
     * lab async work or rely on the default {@code @Async} executor configuration.
     */
    @Bean(name = "documentIngestionExecutor")
    public Executor documentIngestionExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(4);
        ex.setMaxPoolSize(8);
        ex.setQueueCapacity(200);
        ex.setThreadNamePrefix("doc-ingest-");
        ex.initialize();
        return ex;
    }

    @Bean(name = "labJobSseExecutor", destroyMethod = "shutdown")
    public ScheduledExecutorService labJobSseExecutor() {
        return Executors.newScheduledThreadPool(
                6,
                r -> {
                    Thread t = new Thread(r, "lab-job-sse");
                    t.setDaemon(true);
                    return t;
                });
    }
}

package com.uniovi.rag.domain.research;

/**
 * Design notes for long-running jobs (evaluation, training, indexation), not executable logic.
 *
 * <p><strong>In-process {@code @Async}</strong> works for a single JVM. Horizontal scaling without
 * coordination can duplicate work if the same task id is claimed twice; mitigate with:
 * <ul>
 *   <li>Database row locking / lease column when claiming {@code async_task}</li>
 *   <li>External queue (Redis, RabbitMQ) with consumer groups</li>
 *   <li>Idempotent handlers and retry with backoff</li>
 * </ul>
 *
 * <p>Lab jobs must remain report-only for production configuration unless an explicit promotion flow exists.
 */
public final class LongRunningJobNotes {

    private LongRunningJobNotes() {
    }
}

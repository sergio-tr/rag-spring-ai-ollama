package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.domain.AsyncTaskStatus;
import com.uniovi.rag.domain.LabJobEventType;
import com.uniovi.rag.infrastructure.persistence.AsyncTaskRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Marks orphaned RUNNING/CANCELLING lab tasks as failed after an unclean shutdown (stale heartbeat).
 */
@Component
public class LabJobStaleReconciliationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LabJobStaleReconciliationRunner.class);
    private static final Duration STALE_AFTER = Duration.ofHours(6);

    private final AsyncTaskRepository asyncTaskRepository;
    private final LabJobEventService labJobEventService;

    public LabJobStaleReconciliationRunner(
            AsyncTaskRepository asyncTaskRepository, LabJobEventService labJobEventService) {
        this.asyncTaskRepository = asyncTaskRepository;
        this.labJobEventService = labJobEventService;
    }

    @Override
    public void run(ApplicationArguments args) {
        Instant cutoff = Instant.now().minus(STALE_AFTER);
        List<AsyncTaskEntity> stale =
                asyncTaskRepository.findByStatusInAndUpdatedAtBefore(
                        List.of(AsyncTaskStatus.RUNNING, AsyncTaskStatus.CANCELLING), cutoff);
        if (stale.isEmpty()) {
            return;
        }
        log.warn("lab_job_stale_reconciliation count={} cutoff={}", stale.size(), cutoff);
        for (AsyncTaskEntity task : stale) {
            AsyncTaskStatus previous = task.getStatus();
            Instant now = Instant.now();
            if (previous == AsyncTaskStatus.CANCELLING) {
                task.setStatus(AsyncTaskStatus.CANCELLED);
                task.setErrorMessage("Cancelled after unclean shutdown (stale job)");
            } else {
                task.setStatus(AsyncTaskStatus.FAILED);
                task.setErrorMessage("Abandoned after unclean shutdown (stale job)");
            }
            task.setCompletedAt(now);
            task.setUpdatedAt(now);
            asyncTaskRepository.save(task);
            LabJobEventType type =
                    previous == AsyncTaskStatus.CANCELLING
                            ? LabJobEventType.CANCELLED
                            : LabJobEventType.FAILED;
            labJobEventService.recordEvent(task.getId(), type, task.getErrorMessage());
            log.info(
                    "lab_job_stale_reconciled taskId={} previousStatus={} newStatus={}",
                    task.getId(),
                    previous,
                    task.getStatus());
        }
    }
}

package com.uniovi.rag.application.service.async;

import com.uniovi.rag.domain.AsyncTaskStatus;
import com.uniovi.rag.infrastructure.persistence.AsyncTaskRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AsyncTaskCancellationService {

    private final AsyncTaskRepository asyncTaskRepository;

    public AsyncTaskCancellationService(AsyncTaskRepository asyncTaskRepository) {
        this.asyncTaskRepository = asyncTaskRepository;
    }

    @Transactional(readOnly = true)
    public void throwIfCancellationRequested(UUID taskId) {
        if (taskId == null) {
            return;
        }
        AsyncTaskEntity e = asyncTaskRepository.findById(taskId).orElse(null);
        if (e == null || e.getStatus() == null) {
            return;
        }
        if (e.getStatus() == AsyncTaskStatus.CANCELLING || e.getStatus() == AsyncTaskStatus.CANCELLED) {
            throw new LabJobCancelledException("Cancellation requested by user");
        }
    }
}


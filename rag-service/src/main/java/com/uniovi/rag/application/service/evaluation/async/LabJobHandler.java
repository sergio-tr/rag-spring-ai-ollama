package com.uniovi.rag.application.service.evaluation.async;

import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.application.service.async.AsyncTaskMutationService;

/**
 * One handler per {@link AsyncTaskType}; keeps {@link com.uniovi.rag.application.service.async.AsyncLabTaskRunner} as a thin dispatcher.
 */
public interface LabJobHandler {

    AsyncTaskType taskType();

    void run(AsyncTaskEntity task, AsyncTaskMutationService mutation);
}

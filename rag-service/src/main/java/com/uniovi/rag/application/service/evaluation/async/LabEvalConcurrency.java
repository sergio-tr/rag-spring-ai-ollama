package com.uniovi.rag.application.service.evaluation.async;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Shared lock: {@link com.uniovi.rag.application.service.evaluation.EvaluationService} uses mutable dataset state; serialize eval jobs.
 */
public final class LabEvalConcurrency {

    public static final ReentrantLock SERIAL_EVAL = new ReentrantLock();

    private LabEvalConcurrency() {
    }
}

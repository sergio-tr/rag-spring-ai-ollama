package com.uniovi.rag.service.async.lab;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Shared lock: {@link com.uniovi.rag.service.evaluation.EvaluationService} uses mutable dataset state; serialize eval jobs.
 */
public final class LabEvalConcurrency {

    public static final ReentrantLock SERIAL_EVAL = new ReentrantLock();

    private LabEvalConcurrency() {
    }
}

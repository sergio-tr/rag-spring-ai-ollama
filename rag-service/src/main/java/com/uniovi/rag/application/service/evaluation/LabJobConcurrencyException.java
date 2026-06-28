package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.interfaces.rest.dto.ActiveLabJobDto;

/**
 * Signals that a Lab evaluation cannot be started because an incompatible job is already running.
 */
public final class LabJobConcurrencyException extends RuntimeException {

    private final ActiveLabJobDto activeJob;

    public LabJobConcurrencyException(String message, ActiveLabJobDto activeJob) {
        super(message != null ? message : "A Lab job is already running");
        this.activeJob = activeJob;
    }

    public ActiveLabJobDto activeJob() {
        return activeJob;
    }
}


package com.uniovi.rag.application.service.runtime.clarification;

/**
 * Outcome of load + optional invalid recovery + refiner before QU (P11).
 */
public record ClarificationBootstrap(
        String effectivePlanningInputText,
        boolean pendingClarificationLoadedForTrace,
        boolean validPendingExistedAtLoad,
        boolean invalidPendingRecoveredThisTurn) {}

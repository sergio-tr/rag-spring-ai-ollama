package com.uniovi.rag.interfaces.rest.dto.tracereplay;

/**
 * Bounded summary of the transient replay {@link com.uniovi.rag.domain.runtime.engine.ExecutionTrace} for P22 HTTP.
 */
public record RuntimeTraceReplayTransientTraceSummaryDto(int stageCount) {}

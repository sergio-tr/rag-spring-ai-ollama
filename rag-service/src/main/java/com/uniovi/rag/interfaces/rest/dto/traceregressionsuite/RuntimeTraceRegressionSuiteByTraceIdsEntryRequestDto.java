package com.uniovi.rag.interfaces.rest.dto.traceregressionsuite;

import java.util.List;
import java.util.UUID;

/** {@code kind: BY_TRACE_IDS} entry (P31). */
public record RuntimeTraceRegressionSuiteByTraceIdsEntryRequestDto(String kind, List<UUID> traceIds)
        implements RuntimeTraceRegressionSuiteEntryRequestDto {}

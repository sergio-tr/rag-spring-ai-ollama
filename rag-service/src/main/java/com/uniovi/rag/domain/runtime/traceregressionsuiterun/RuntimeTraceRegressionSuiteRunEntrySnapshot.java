package com.uniovi.rag.domain.runtime.traceregressionsuiterun;

import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteEntryFailureKind;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteEntryKind;
import com.uniovi.rag.domain.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchOutcome;

import java.util.Optional;

/** One persisted entry row rehydrated as domain data (P41). */
public record RuntimeTraceRegressionSuiteRunEntrySnapshot(
        short entryOrder,
        RuntimeTraceRegressionSuiteEntryKind entryKind,
        String selectorEcho,
        RuntimeTraceRegressionSuiteRunPersistedExecutionStatus executionStatus,
        Optional<RuntimeTraceReplayComparisonBatchOutcome> batchOutcome,
        Optional<Integer> requestedCount,
        Optional<Integer> selectedCount,
        Optional<Integer> processedCount,
        Optional<RuntimeTraceRegressionSuiteEntryFailureKind> failureKind,
        Optional<String> failureDetail) {}

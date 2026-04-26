package com.uniovi.rag.application.service.runtime.traceregressionsuiterun;

import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteBatchReturnedEntryResult;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteEntryResult;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteExecutionFailedEntryResult;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteOutcome;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteResult;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteSummary;
import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunSnapshot;
import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunSourceType;
import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunSummary;
import com.uniovi.rag.infrastructure.persistence.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunEntity;
import com.uniovi.rag.infrastructure.persistence.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunEntryEntity;
import com.uniovi.rag.infrastructure.persistence.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunEntryRepository;
import com.uniovi.rag.infrastructure.persistence.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunPersistenceMapper;
import com.uniovi.rag.infrastructure.persistence.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * P41 — run snapshot persistence adapter: writes and reads {@code runtime_trace_regression_suite_run*} only from a
 * supplied {@link RuntimeTraceRegressionSuiteResult}. Does not execute suites or touch definition services.
 *
 * <p>P48 — {@link #deleteRunForUser} removes a run row for the owning user; entry rows are removed only via DB {@code ON
 * DELETE CASCADE} (no application deletes against {@code runtime_trace_regression_suite_run_entry}).
 *
 * <p>P50 — {@link #listSummariesForUserAndDefinition} and {@link #loadByIdForUserAndDefinition} scope reads by owning user
 * and saved-definition id (single-query detail path).
 *
 * <p>P51 — {@link #deleteRunForUserAndDefinition} deletes a run row only when {@code id}, {@code user_id}, and
 * {@code definition_id} all match; entries cascade via DB only (same as P48). {@link #deleteRunForUser} remains the global
 * two-column delete (unchanged).
 */
@Service
public class RuntimeTraceRegressionSuiteRunPersistenceService {

    private static final int SELECTOR_ECHO_MAX_CODE_POINTS = 256;
    private static final int FAILURE_DETAIL_MAX_CODE_POINTS = 1024;

    private final RuntimeTraceRegressionSuiteRunRepository runRepository;
    private final RuntimeTraceRegressionSuiteRunEntryRepository entryRepository;
    private final RuntimeTraceRegressionSuiteRunPersistenceMapper mapper;
    private final Clock clock;

    public RuntimeTraceRegressionSuiteRunPersistenceService(
            RuntimeTraceRegressionSuiteRunRepository runRepository,
            RuntimeTraceRegressionSuiteRunEntryRepository entryRepository,
            RuntimeTraceRegressionSuiteRunPersistenceMapper mapper,
            @Autowired(required = false) Clock clock) {
        this.runRepository = runRepository;
        this.entryRepository = entryRepository;
        this.mapper = mapper;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    @Transactional
    public UUID createRun(
            UUID userId,
            RuntimeTraceRegressionSuiteRunSourceType sourceType,
            Optional<UUID> definitionId,
            RuntimeTraceRegressionSuiteResult result) {
        validateCreate(userId, sourceType, definitionId, result);

        UUID id = UUID.randomUUID();
        RuntimeTraceRegressionSuiteSummary s = result.summary();
        RuntimeTraceRegressionSuiteRunEntity run = new RuntimeTraceRegressionSuiteRunEntity();
        run.setId(id);
        run.setUserId(userId);
        run.setSourceType(sourceType);
        run.setDefinitionId(definitionId.orElse(null));
        run.setSuiteOutcome(result.suiteOutcome());
        run.setRequestedEntryCount(s.requestedEntryCount());
        run.setProcessedEntryCount(s.processedEntryCount());
        run.setBatchReturnedCount(s.batchReturnedCount());
        run.setExecutionFailedCount(s.executionFailedCount());
        run.setBatchNotAttemptedSubcount(s.batchNotAttemptedSubcount());
        run.setCreatedAt(clock.instant());
        runRepository.save(run);
        // JdbcTemplate assertions in integration tests run in the same transaction; force inserts now.
        runRepository.flush();

        List<RuntimeTraceRegressionSuiteEntryResult> rows = result.entryResults();
        List<RuntimeTraceRegressionSuiteRunEntryEntity> toSave = new ArrayList<>(rows.size());
        for (RuntimeTraceRegressionSuiteEntryResult row : rows) {
            String echo =
                    switch (row) {
                        case RuntimeTraceRegressionSuiteBatchReturnedEntryResult br ->
                                capCodePoints(br.selectorEcho(), SELECTOR_ECHO_MAX_CODE_POINTS);
                        case RuntimeTraceRegressionSuiteExecutionFailedEntryResult fr ->
                                capCodePoints(fr.selectorEcho(), SELECTOR_ECHO_MAX_CODE_POINTS);
                    };
            RuntimeTraceRegressionSuiteEntryResult mapped = row;
            if (row instanceof RuntimeTraceRegressionSuiteExecutionFailedEntryResult fr) {
                String detail = capCodePoints(fr.failureDetail(), FAILURE_DETAIL_MAX_CODE_POINTS);
                mapped =
                        new RuntimeTraceRegressionSuiteExecutionFailedEntryResult(
                                fr.entryOrder(), fr.entryKind(), fr.selectorEcho(), fr.failureKind(), detail);
            }
            toSave.add(mapper.newEntryEntity(UUID.randomUUID(), id, mapped, echo));
        }
        entryRepository.saveAll(toSave);
        entryRepository.flush();
        return id;
    }

    private static void validateCreate(
            UUID userId,
            RuntimeTraceRegressionSuiteRunSourceType sourceType,
            Optional<UUID> definitionId,
            RuntimeTraceRegressionSuiteResult result) {
        validateCreateInputs(userId, sourceType, definitionId, result);
        validateOutcome(result);
        RuntimeTraceRegressionSuiteSummary sum = result.summary();
        validateSummary(sum, result.entryResults().size());
        validateEntryOrders(result.entryResults());
    }

    private static void validateCreateInputs(
            UUID userId,
            RuntimeTraceRegressionSuiteRunSourceType sourceType,
            Optional<UUID> definitionId,
            RuntimeTraceRegressionSuiteResult result) {
        if (userId == null) {
            throw new IllegalArgumentException("userId");
        }
        if (result == null) {
            throw new IllegalArgumentException("result");
        }
        if (sourceType == RuntimeTraceRegressionSuiteRunSourceType.SAVED_DEFINITION && definitionId.isEmpty()) {
            throw new IllegalArgumentException("definitionId required for SAVED_DEFINITION");
        }
        if (sourceType == RuntimeTraceRegressionSuiteRunSourceType.AD_HOC && definitionId.isPresent()) {
            throw new IllegalArgumentException("definitionId must be absent for AD_HOC");
        }
    }

    private static void validateOutcome(RuntimeTraceRegressionSuiteResult result) {
        if (result.suiteOutcome() == RuntimeTraceRegressionSuiteOutcome.NOT_ATTEMPTED
                && !result.entryResults().isEmpty()) {
            throw new IllegalArgumentException("NOT_ATTEMPTED result must not contain entry results");
        }
        if (result.suiteOutcome() == RuntimeTraceRegressionSuiteOutcome.EMPTY_SUITE
                && !result.entryResults().isEmpty()) {
            throw new IllegalArgumentException("EMPTY_SUITE result must not contain entry results");
        }
    }

    private static void validateSummary(RuntimeTraceRegressionSuiteSummary sum, int actualEntryCount) {
        if (sum.requestedEntryCount() < 0
                || sum.processedEntryCount() < 0
                || sum.batchReturnedCount() < 0
                || sum.executionFailedCount() < 0
                || sum.batchNotAttemptedSubcount() < 0) {
            throw new IllegalArgumentException("result summary invalid");
        }
        if (actualEntryCount != sum.requestedEntryCount()) {
            throw new IllegalArgumentException("result entryResults size mismatch");
        }
    }

    private static void validateEntryOrders(List<RuntimeTraceRegressionSuiteEntryResult> rows) {
        for (int i = 0; i < rows.size(); i++) {
            RuntimeTraceRegressionSuiteEntryResult row = rows.get(i);
            int order =
                    switch (row) {
                        case RuntimeTraceRegressionSuiteBatchReturnedEntryResult br -> br.entryOrder();
                        case RuntimeTraceRegressionSuiteExecutionFailedEntryResult fr -> fr.entryOrder();
                    };
            if (order != i) {
                throw new IllegalArgumentException("entryOrder mismatch");
            }
            if (order < 0 || order > 19) {
                throw new IllegalArgumentException("entryOrder out of range");
            }
        }
    }

    @Transactional(readOnly = true)
    public Optional<RuntimeTraceRegressionSuiteRunSnapshot> loadByIdForUser(UUID runId, UUID userId) {
        if (runId == null) {
            throw new IllegalArgumentException("runId");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId");
        }
        Optional<RuntimeTraceRegressionSuiteRunEntity> run = runRepository.findByIdAndUserId(runId, userId);
        if (run.isEmpty()) {
            return Optional.empty();
        }
        List<RuntimeTraceRegressionSuiteRunEntryEntity> entries =
                entryRepository.findAllByRunIdOrderByEntryOrderAsc(runId);
        return Optional.of(mapper.toSnapshot(run.get(), entries));
    }

    @Transactional(readOnly = true)
    public List<RuntimeTraceRegressionSuiteRunSummary> listSummariesForUser(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId");
        }
        List<RuntimeTraceRegressionSuiteRunEntity> rows = runRepository.findAllByUserIdOrderByCreatedAtDescIdAsc(userId);
        List<RuntimeTraceRegressionSuiteRunSummary> out = new ArrayList<>(rows.size());
        for (RuntimeTraceRegressionSuiteRunEntity row : rows) {
            out.add(mapper.toSummary(row));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<RuntimeTraceRegressionSuiteRunSummary> listSummariesForUserAndDefinition(
            UUID userId, UUID definitionId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId");
        }
        if (definitionId == null) {
            throw new IllegalArgumentException("definitionId");
        }
        List<RuntimeTraceRegressionSuiteRunEntity> rows =
                runRepository.findAllByUserIdAndDefinitionIdOrderByCreatedAtDescIdAsc(userId, definitionId);
        List<RuntimeTraceRegressionSuiteRunSummary> out = new ArrayList<>(rows.size());
        for (RuntimeTraceRegressionSuiteRunEntity row : rows) {
            out.add(mapper.toSummary(row));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Optional<RuntimeTraceRegressionSuiteRunSnapshot> loadByIdForUserAndDefinition(
            UUID runId, UUID userId, UUID definitionId) {
        if (runId == null) {
            throw new IllegalArgumentException("runId");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId");
        }
        if (definitionId == null) {
            throw new IllegalArgumentException("definitionId");
        }
        Optional<RuntimeTraceRegressionSuiteRunEntity> run =
                runRepository.findByIdAndUserIdAndDefinitionId(runId, userId, definitionId);
        if (run.isEmpty()) {
            return Optional.empty();
        }
        List<RuntimeTraceRegressionSuiteRunEntryEntity> entries =
                entryRepository.findAllByRunIdOrderByEntryOrderAsc(runId);
        return Optional.of(mapper.toSnapshot(run.get(), entries));
    }

    @Transactional
    public boolean deleteRunForUser(UUID runId, UUID userId) {
        if (runId == null) {
            throw new IllegalArgumentException("runId");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId");
        }
        long deleted = runRepository.deleteByIdAndUserId(runId, userId);
        if (deleted == 1L) {
            runRepository.flush();
        }
        return deleted == 1L;
    }

    @Transactional
    public boolean deleteRunForUserAndDefinition(UUID runId, UUID userId, UUID definitionId) {
        if (runId == null) {
            throw new IllegalArgumentException("runId");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId");
        }
        if (definitionId == null) {
            throw new IllegalArgumentException("definitionId");
        }
        long deleted = runRepository.deleteByIdAndUserIdAndDefinitionId(runId, userId, definitionId);
        if (deleted == 1L) {
            runRepository.flush();
        }
        return deleted == 1L;
    }

    /**
     * Truncates to at most {@code max} Unicode code points (not Java {@code char} units) — same rule as P30 suite
     * service without depending on it.
     */
    private static String capCodePoints(String s, int max) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return s.codePoints()
                .limit(max)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }
}

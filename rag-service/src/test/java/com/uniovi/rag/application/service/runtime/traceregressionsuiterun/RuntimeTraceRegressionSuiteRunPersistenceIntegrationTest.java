package com.uniovi.rag.application.service.runtime.traceregressionsuiterun;

import com.uniovi.Application;
import com.uniovi.rag.domain.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchOutcome;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteBatchReturnedEntryResult;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteEntryFailureKind;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteEntryKind;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteExecutionFailedEntryResult;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteOutcome;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteResult;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteSummary;
import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunSourceType;
import com.uniovi.rag.testsupport.TestAiStubConfiguration;
import com.uniovi.rag.testsupport.TestcontainersDatasourceConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        classes = Application.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "rag.jwt.secret=test-secret-key-for-jwt-signing-must-be-long-enough-32",
            "management.otlp.tracing.endpoint=http://127.0.0.1:4318/v1/traces",
            "management.otlp.metrics.export.url=http://127.0.0.1:4318/v1/metrics"
        })
@Import({TestAiStubConfiguration.class, TestcontainersDatasourceConfiguration.class})
@ActiveProfiles("test")
@Transactional
@EnabledIf(
        value = "com.uniovi.rag.testsupport.TestEnvironment#isSpringBootPostgresAvailable",
        disabledReason = "Postgres/Testcontainers not available")
class RuntimeTraceRegressionSuiteRunPersistenceIntegrationTest {

    @Autowired
    private RuntimeTraceRegressionSuiteRunPersistenceService runPersistenceService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static RuntimeTraceRegressionSuiteResult oneBatchEntry() {
        var entry =
                new RuntimeTraceRegressionSuiteBatchReturnedEntryResult(
                        0,
                        RuntimeTraceRegressionSuiteEntryKind.BY_TRACE_IDS,
                        "BY_TRACE_IDS:t1",
                        RuntimeTraceReplayComparisonBatchOutcome.COMPLETED_ALL_EXACT_MATCH,
                        3,
                        3,
                        3);
        var summary = new RuntimeTraceRegressionSuiteSummary(1, 1, 1, 0, 0);
        return new RuntimeTraceRegressionSuiteResult(
                RuntimeTraceRegressionSuiteOutcome.COMPLETED_ALL_BATCH_RETURNS, summary, List.of(entry));
    }

    @Test
    void t5_adHoc_roundTrip() {
        UUID userId = UUID.randomUUID();
        UUID id =
                runPersistenceService.createRun(
                        userId, RuntimeTraceRegressionSuiteRunSourceType.AD_HOC, Optional.empty(), oneBatchEntry());
        var loaded = runPersistenceService.loadByIdForUser(id, userId);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().definitionId()).isEmpty();
        assertThat(loaded.get().entries()).hasSize(1);
        assertThat(loaded.get().entries().getFirst().selectorEcho()).isEqualTo("BY_TRACE_IDS:t1");
        assertThat(loaded.get().summary().batchReturnedCount()).isEqualTo(1);
    }

    @Test
    void t6_savedDefinition_roundTrip() {
        UUID userId = UUID.randomUUID();
        UUID defId = UUID.randomUUID();
        UUID id =
                runPersistenceService.createRun(
                        userId,
                        RuntimeTraceRegressionSuiteRunSourceType.SAVED_DEFINITION,
                        Optional.of(defId),
                        oneBatchEntry());
        var loaded = runPersistenceService.loadByIdForUser(id, userId);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().definitionId()).contains(defId);
    }

    @Test
    void t7_loadWrongUser_empty() {
        UUID userId = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID id =
                runPersistenceService.createRun(
                        userId, RuntimeTraceRegressionSuiteRunSourceType.AD_HOC, Optional.empty(), oneBatchEntry());
        assertThat(runPersistenceService.loadByIdForUser(id, other)).isEmpty();
    }

    @Test
    void t8_listSummaries_newerFirst() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID older =
                runPersistenceService.createRun(
                        userId, RuntimeTraceRegressionSuiteRunSourceType.AD_HOC, Optional.empty(), oneBatchEntry());
        Thread.sleep(5);
        UUID newer =
                runPersistenceService.createRun(
                        userId, RuntimeTraceRegressionSuiteRunSourceType.AD_HOC, Optional.empty(), oneBatchEntry());
        var list = runPersistenceService.listSummariesForUser(userId);
        assertThat(list).extracting(s -> s.id().value()).startsWith(newer, older);
    }

    @Test
    void t9_deleteRun_cascadesEntries() {
        UUID userId = UUID.randomUUID();
        UUID id =
                runPersistenceService.createRun(
                        userId, RuntimeTraceRegressionSuiteRunSourceType.AD_HOC, Optional.empty(), oneBatchEntry());
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM runtime_trace_regression_suite_run_entry WHERE run_id = ?",
                                Integer.class,
                                id))
                .isEqualTo(1);
        jdbcTemplate.update("DELETE FROM runtime_trace_regression_suite_run WHERE id = ?", id);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM runtime_trace_regression_suite_run_entry WHERE run_id = ?",
                                Integer.class,
                                id))
                .isZero();
    }

    @Test
    void t11_mixedBatchReturnedAndExecutionFailed() {
        UUID userId = UUID.randomUUID();
        var batch =
                new RuntimeTraceRegressionSuiteBatchReturnedEntryResult(
                        0,
                        RuntimeTraceRegressionSuiteEntryKind.BY_TRACE_IDS,
                        "a",
                        RuntimeTraceReplayComparisonBatchOutcome.COMPLETED_MIXED,
                        1,
                        1,
                        1);
        var failed =
                new RuntimeTraceRegressionSuiteExecutionFailedEntryResult(
                        1,
                        RuntimeTraceRegressionSuiteEntryKind.BY_CONVERSATION,
                        "b",
                        RuntimeTraceRegressionSuiteEntryFailureKind.ILLEGAL_ARGUMENT,
                        "detail");
        var summary = new RuntimeTraceRegressionSuiteSummary(2, 2, 1, 1, 0);
        var result =
                new RuntimeTraceRegressionSuiteResult(
                        RuntimeTraceRegressionSuiteOutcome.COMPLETED_WITH_ENTRY_EXECUTION_FAILURES,
                        summary,
                        List.of(batch, failed));
        UUID id =
                runPersistenceService.createRun(
                        userId, RuntimeTraceRegressionSuiteRunSourceType.AD_HOC, Optional.empty(), result);
        var loaded = runPersistenceService.loadByIdForUser(id, userId).orElseThrow();
        assertThat(loaded.entries()).hasSize(2);
        assertThat(loaded.entries().get(0).executionStatus().name()).isEqualTo("BATCH_RETURNED");
        assertThat(loaded.entries().get(1).executionStatus().name()).isEqualTo("EXECUTION_FAILED");
    }

    @Test
    void t13_invalidAdHocWithDefinitionId_constraint() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant now = Instant.parse("2026-03-01T00:00:00Z");
        Timestamp ts = Timestamp.from(now);
        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        """
                                        INSERT INTO runtime_trace_regression_suite_run
                                        (id, user_id, source_type, definition_id, suite_outcome,
                                         requested_entry_count, processed_entry_count, batch_returned_count,
                                         execution_failed_count, batch_not_attempted_subcount, created_at)
                                        VALUES (?,?,?,?,?,?,?,?,?,?,?)
                                        """,
                                        id,
                                        userId,
                                        "AD_HOC",
                                        UUID.randomUUID(),
                                        "NOT_ATTEMPTED",
                                        0,
                                        0,
                                        0,
                                        0,
                                        0,
                                        ts))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void t14_executionFailedWithBatchOutcome_constraint() {
        UUID runId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant now = Instant.parse("2026-03-02T00:00:00Z");
        Timestamp ts = Timestamp.from(now);
        jdbcTemplate.update(
                """
                INSERT INTO runtime_trace_regression_suite_run
                (id, user_id, source_type, definition_id, suite_outcome,
                 requested_entry_count, processed_entry_count, batch_returned_count,
                 execution_failed_count, batch_not_attempted_subcount, created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """,
                runId,
                userId,
                "AD_HOC",
                null,
                "EMPTY_SUITE",
                0,
                0,
                0,
                0,
                0,
                ts);
        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        """
                                        INSERT INTO runtime_trace_regression_suite_run_entry
                                        (id, run_id, entry_order, entry_kind, selector_echo, execution_status,
                                         batch_outcome, requested_count, selected_count, processed_count,
                                         failure_kind, failure_detail)
                                        VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                                        """,
                                        UUID.randomUUID(),
                                        runId,
                                        0,
                                        "BY_TRACE_IDS",
                                        "x",
                                        "EXECUTION_FAILED",
                                        "COMPLETED_MIXED",
                                        null,
                                        null,
                                        null,
                                        "ILLEGAL_ARGUMENT",
                                        null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void t15_listTieBreakOrderByIdAsc() {
        UUID userId = UUID.randomUUID();
        Instant same = Instant.parse("2026-04-01T12:00:00Z");
        Timestamp ts = Timestamp.from(same);
        UUID idA =
                runPersistenceService.createRun(
                        userId, RuntimeTraceRegressionSuiteRunSourceType.AD_HOC, Optional.empty(), oneBatchEntry());
        UUID idB =
                runPersistenceService.createRun(
                        userId, RuntimeTraceRegressionSuiteRunSourceType.AD_HOC, Optional.empty(), oneBatchEntry());
        jdbcTemplate.update(
                "UPDATE runtime_trace_regression_suite_run SET created_at = ? WHERE id IN (?, ?)", ts, idA, idB);

        // Postgres UUID ordering (ORDER BY id) is not guaranteed to match Java UUID.compareTo().
        // Verify against the actual DB sort we require: created_at DESC, id ASC.
        List<UUID> dbOrdered =
                jdbcTemplate.queryForList(
                        "SELECT id FROM runtime_trace_regression_suite_run WHERE user_id = ? ORDER BY created_at DESC, id ASC",
                        UUID.class,
                        userId);
        assertThat(dbOrdered).contains(idA, idB);

        var list = runPersistenceService.listSummariesForUser(userId);
        int idxA = -1;
        int idxB = -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id().value().equals(idA)) {
                idxA = i;
            }
            if (list.get(i).id().value().equals(idB)) {
                idxB = i;
            }
        }
        assertThat(idxA).isGreaterThanOrEqualTo(0);
        assertThat(idxB).isGreaterThanOrEqualTo(0);
        if (dbOrdered.get(0).equals(idA)) {
            assertThat(idxA).isLessThan(idxB);
        } else if (dbOrdered.get(0).equals(idB)) {
            assertThat(idxB).isLessThan(idxA);
        } else {
            throw new IllegalStateException("unexpected db ordering for inserted runs");
        }
    }

    @Test
    void p48_t7_delete_removes_run_then_load_empty() {
        UUID userA = UUID.randomUUID();
        UUID runId =
                runPersistenceService.createRun(
                        userA, RuntimeTraceRegressionSuiteRunSourceType.AD_HOC, Optional.empty(), oneBatchEntry());
        assertThat(runPersistenceService.deleteRunForUser(runId, userA)).isTrue();
        assertThat(runPersistenceService.loadByIdForUser(runId, userA)).isEmpty();
    }

    @Test
    void p48_t8_delete_random_id_false() {
        assertThat(runPersistenceService.deleteRunForUser(UUID.randomUUID(), UUID.randomUUID())).isFalse();
    }

    @Test
    void p48_t9_wrong_user_delete_false_entries_unchanged() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        UUID runId =
                runPersistenceService.createRun(
                        userA, RuntimeTraceRegressionSuiteRunSourceType.AD_HOC, Optional.empty(), oneBatchEntry());
        int entriesBefore =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM runtime_trace_regression_suite_run_entry WHERE run_id = ?",
                        Integer.class,
                        runId);
        assertThat(entriesBefore).isEqualTo(1);
        assertThat(runPersistenceService.deleteRunForUser(runId, userB)).isFalse();
        assertThat(runPersistenceService.loadByIdForUser(runId, userA)).isPresent();
        int entriesAfter =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM runtime_trace_regression_suite_run_entry WHERE run_id = ?",
                        Integer.class,
                        runId);
        assertThat(entriesAfter).isEqualTo(entriesBefore);
    }

    @Test
    void p48_t10_delete_removes_run_and_cascade_entries() {
        UUID userId = UUID.randomUUID();
        UUID runId =
                runPersistenceService.createRun(
                        userId, RuntimeTraceRegressionSuiteRunSourceType.AD_HOC, Optional.empty(), oneBatchEntry());
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM runtime_trace_regression_suite_run_entry WHERE run_id = ?",
                                Integer.class,
                                runId))
                .isEqualTo(1);
        assertThat(runPersistenceService.deleteRunForUser(runId, userId)).isTrue();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM runtime_trace_regression_suite_run WHERE id = ?", Integer.class, runId))
                .isZero();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM runtime_trace_regression_suite_run_entry WHERE run_id = ?",
                                Integer.class,
                                runId))
                .isZero();
    }

    @Test
    void p48_t11_delete_first_second_still_loadable() {
        UUID userId = UUID.randomUUID();
        UUID run1 =
                runPersistenceService.createRun(
                        userId, RuntimeTraceRegressionSuiteRunSourceType.AD_HOC, Optional.empty(), oneBatchEntry());
        UUID run2 =
                runPersistenceService.createRun(
                        userId, RuntimeTraceRegressionSuiteRunSourceType.AD_HOC, Optional.empty(), oneBatchEntry());
        assertThat(runPersistenceService.deleteRunForUser(run1, userId)).isTrue();
        assertThat(runPersistenceService.loadByIdForUser(run2, userId)).isPresent();
    }

    @Test
    void p48_t12_delete_first_preserves_second_entry_count() {
        UUID userId = UUID.randomUUID();
        UUID run1 =
                runPersistenceService.createRun(
                        userId, RuntimeTraceRegressionSuiteRunSourceType.AD_HOC, Optional.empty(), oneBatchEntry());
        UUID run2 =
                runPersistenceService.createRun(
                        userId, RuntimeTraceRegressionSuiteRunSourceType.AD_HOC, Optional.empty(), oneBatchEntry());
        int e2 =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM runtime_trace_regression_suite_run_entry WHERE run_id = ?",
                        Integer.class,
                        run2);
        assertThat(e2).isGreaterThanOrEqualTo(1);
        assertThat(runPersistenceService.deleteRunForUser(run1, userId)).isTrue();
        int e2After =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM runtime_trace_regression_suite_run_entry WHERE run_id = ?",
                        Integer.class,
                        run2);
        assertThat(e2After).isEqualTo(e2);
    }

    @Test
    void p48_t13_wrong_user_delete_row_still_owned_by_user_a() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        UUID runId =
                runPersistenceService.createRun(
                        userA, RuntimeTraceRegressionSuiteRunSourceType.AD_HOC, Optional.empty(), oneBatchEntry());
        assertThat(runPersistenceService.deleteRunForUser(runId, userB)).isFalse();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM runtime_trace_regression_suite_run WHERE id = ? AND user_id = ?",
                                Integer.class,
                                runId,
                                userA))
                .isEqualTo(1);
    }

    @Test
    void p51_t9_deleteForDefinition_removes_saved_run_then_load_empty() {
        UUID userA = UUID.randomUUID();
        UUID defId = UUID.randomUUID();
        UUID runId =
                runPersistenceService.createRun(
                        userA,
                        RuntimeTraceRegressionSuiteRunSourceType.SAVED_DEFINITION,
                        Optional.of(defId),
                        oneBatchEntry());
        assertThat(runPersistenceService.deleteRunForUserAndDefinition(runId, userA, defId)).isTrue();
        assertThat(runPersistenceService.loadByIdForUser(runId, userA)).isEmpty();
    }

    @Test
    void p51_t10_wrong_definition_delete_false_run_still_present() {
        UUID userA = UUID.randomUUID();
        UUID defId = UUID.randomUUID();
        UUID otherDefId = UUID.randomUUID();
        UUID runId =
                runPersistenceService.createRun(
                        userA,
                        RuntimeTraceRegressionSuiteRunSourceType.SAVED_DEFINITION,
                        Optional.of(defId),
                        oneBatchEntry());
        assertThat(runPersistenceService.deleteRunForUserAndDefinition(runId, userA, otherDefId)).isFalse();
        assertThat(runPersistenceService.loadByIdForUser(runId, userA)).isPresent();
    }

    @Test
    void p51_t11_ad_hoc_delete_with_definition_id_false_row_remains() {
        UUID userA = UUID.randomUUID();
        UUID someDefId = UUID.randomUUID();
        UUID adhocRunId =
                runPersistenceService.createRun(
                        userA, RuntimeTraceRegressionSuiteRunSourceType.AD_HOC, Optional.empty(), oneBatchEntry());
        assertThat(runPersistenceService.deleteRunForUserAndDefinition(adhocRunId, userA, someDefId)).isFalse();
        assertThat(runPersistenceService.loadByIdForUser(adhocRunId, userA)).isPresent();
    }

    @Test
    void p51_t12_delete_random_id_false() {
        assertThat(
                        runPersistenceService.deleteRunForUserAndDefinition(
                                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))
                .isFalse();
    }

    @Test
    void p51_t13_deleteForDefinition_cascades_entries() {
        UUID userId = UUID.randomUUID();
        UUID defId = UUID.randomUUID();
        UUID runId =
                runPersistenceService.createRun(
                        userId,
                        RuntimeTraceRegressionSuiteRunSourceType.SAVED_DEFINITION,
                        Optional.of(defId),
                        oneBatchEntry());
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM runtime_trace_regression_suite_run_entry WHERE run_id = ?",
                                Integer.class,
                                runId))
                .isGreaterThanOrEqualTo(1);
        assertThat(runPersistenceService.deleteRunForUserAndDefinition(runId, userId, defId)).isTrue();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM runtime_trace_regression_suite_run WHERE id = ?", Integer.class, runId))
                .isZero();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM runtime_trace_regression_suite_run_entry WHERE run_id = ?",
                                Integer.class,
                                runId))
                .isZero();
    }

    @Test
    void p51_t14_global_delete_still_works_after_p51() {
        UUID userId = UUID.randomUUID();
        UUID defId = UUID.randomUUID();
        UUID runId =
                runPersistenceService.createRun(
                        userId,
                        RuntimeTraceRegressionSuiteRunSourceType.SAVED_DEFINITION,
                        Optional.of(defId),
                        oneBatchEntry());
        assertThat(runPersistenceService.deleteRunForUser(runId, userId)).isTrue();
        assertThat(runPersistenceService.loadByIdForUser(runId, userId)).isEmpty();
    }
}

package com.uniovi.rag.application.service.runtime.traceregressionsuitedefinition;

import com.uniovi.Application;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.CreateDefinitionCommand;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionEntrySnapshot;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionEntrySpec;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionUserSummary;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionSnapshot;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.UpdateDefinitionCommand;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteEntry;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteRequest;
import com.uniovi.rag.interfaces.rest.NotFoundException;
import com.uniovi.rag.testsupport.TestAiStubConfiguration;
import com.uniovi.rag.testsupport.TestcontainersDatasourceConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

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
class RuntimeTraceRegressionSuiteDefinitionPersistenceIntegrationTest {

    @Autowired private RuntimeTraceRegressionSuiteDefinitionService definitionService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private UUID insertUser() {
        UUID userId = insertUser();
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, role) VALUES (?, ?, ?, ?)",
                userId,
                userId + "@trace-regression.local",
                "{noop}test",
                "USER");
        return userId;
    }

    @Test
    void create_load_roundTrip_and_materialize_matches() {
        UUID userId = insertUser();
        UUID t1 = UUID.randomUUID();
        UUID conv = UUID.randomUUID();
        var create =
                new CreateDefinitionCommand(
                        "  my suite  ",
                        Optional.of("  desc  "),
                        List.of(
                                new RuntimeTraceRegressionSuiteDefinitionEntrySpec.ByTraceIds(List.of(t1)),
                                new RuntimeTraceRegressionSuiteDefinitionEntrySpec.ByConversation(
                                        conv, Optional.empty(), Optional.empty(), Optional.of("  wf  "))));
        UUID id = definitionService.create(userId, create);

        Optional<RuntimeTraceRegressionSuiteDefinitionSnapshot> loaded = definitionService.loadByIdForUser(id, userId);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().name()).isEqualTo("my suite");
        assertThat(loaded.get().description()).isEqualTo("desc");
        assertThat(loaded.get().entries()).hasSize(2);
        assertThat(loaded.get().entries().getFirst()).isInstanceOf(RuntimeTraceRegressionSuiteDefinitionEntrySnapshot.ByTraceIds.class);
        assertThat(((RuntimeTraceRegressionSuiteDefinitionEntrySnapshot.ByTraceIds) loaded.get().entries().getFirst()).traceIds())
                .containsExactly(t1);

        RuntimeTraceRegressionSuiteRequest expected =
                new RuntimeTraceRegressionSuiteRequest(
                        userId,
                        List.of(
                                new RuntimeTraceRegressionSuiteEntry.ByTraceIds(List.of(t1)),
                                new RuntimeTraceRegressionSuiteEntry.ByConversation(
                                        conv, Optional.empty(), Optional.empty(), Optional.of("wf"))));
        assertThat(definitionService.materializeToSuiteRequest(id, userId)).isEqualTo(expected);
    }

    @Test
    void create_twentyEntries_succeeds() {
        UUID userId = insertUser();
        List<RuntimeTraceRegressionSuiteDefinitionEntrySpec> entries =
                IntStream.range(0, 20)
                        .mapToObj(
                                i ->
                                        (RuntimeTraceRegressionSuiteDefinitionEntrySpec)
                                                new RuntimeTraceRegressionSuiteDefinitionEntrySpec.ByTraceIds(
                                                        List.of(UUID.randomUUID())))
                        .toList();
        UUID id = definitionService.create(userId, new CreateDefinitionCommand("twenty", Optional.empty(), entries));
        assertThat(definitionService.loadByIdForUser(id, userId)).isPresent();
    }

    @Test
    void listSummariesForUser_returnsSummariesWithEntryCounts() {
        UUID userId = insertUser();
        UUID id1 =
                definitionService.create(
                        userId,
                        new CreateDefinitionCommand(
                                "alpha",
                                Optional.empty(),
                                List.of(new RuntimeTraceRegressionSuiteDefinitionEntrySpec.ByTraceIds(List.of()))));
        UUID id2 =
                definitionService.create(
                        userId,
                        new CreateDefinitionCommand(
                                "beta",
                                Optional.of("d"),
                                List.of(
                                        new RuntimeTraceRegressionSuiteDefinitionEntrySpec.ByTraceIds(List.of()),
                                        new RuntimeTraceRegressionSuiteDefinitionEntrySpec.ByTraceIds(List.of()))));
        List<RuntimeTraceRegressionSuiteDefinitionUserSummary> list = definitionService.listSummariesForUser(userId);
        assertThat(list).hasSize(2);
        assertThat(list.getFirst().definitionId()).isEqualTo(id2);
        assertThat(list.getFirst().entryCount()).isEqualTo(2);
        assertThat(list.get(1).definitionId()).isEqualTo(id1);
        assertThat(list.get(1).entryCount()).isEqualTo(1);
    }

    @Test
    void load_wrongUser_returnsEmpty() {
        UUID userId = insertUser();
        UUID other = UUID.randomUUID();
        UUID id =
                definitionService.create(
                        userId,
                        new CreateDefinitionCommand(
                                "iso", Optional.empty(), List.of(new RuntimeTraceRegressionSuiteDefinitionEntrySpec.ByTraceIds(List.of()))));
        assertThat(definitionService.loadByIdForUser(id, other)).isEmpty();
    }

    @Test
    void update_delete_materialize_wrongUser_notFound() {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID id =
                definitionService.create(
                        owner,
                        new CreateDefinitionCommand(
                                "x", Optional.empty(), List.of(new RuntimeTraceRegressionSuiteDefinitionEntrySpec.ByTraceIds(List.of()))));
        var upd =
                new UpdateDefinitionCommand(
                        "y", Optional.empty(), List.of(new RuntimeTraceRegressionSuiteDefinitionEntrySpec.ByTraceIds(List.of())));
        assertThatThrownBy(() -> definitionService.update(id, other, upd)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> definitionService.delete(id, other)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> definitionService.materializeToSuiteRequest(id, other)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void fullReplaceUpdate_replacesEntriesAndTraces() {
        UUID userId = insertUser();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID id =
                definitionService.create(
                        userId,
                        new CreateDefinitionCommand(
                                "fr",
                                Optional.empty(),
                                List.of(
                                        new RuntimeTraceRegressionSuiteDefinitionEntrySpec.ByTraceIds(List.of(a)),
                                        new RuntimeTraceRegressionSuiteDefinitionEntrySpec.ByTraceIds(List.of(b)))));
        definitionService.update(
                id,
                userId,
                new UpdateDefinitionCommand(
                        "fr",
                        Optional.empty(),
                        List.of(new RuntimeTraceRegressionSuiteDefinitionEntrySpec.ByTraceIds(List.of(b)))));
        var snap = definitionService.loadByIdForUser(id, userId).orElseThrow();
        assertThat(snap.entries()).hasSize(1);
        assertThat(((RuntimeTraceRegressionSuiteDefinitionEntrySnapshot.ByTraceIds) snap.entries().getFirst()).traceIds())
                .containsExactly(b);
    }

    @Test
    void blankWorkflow_persistedNull_andMaterializedEmpty() {
        UUID userId = insertUser();
        UUID conv = UUID.randomUUID();
        UUID id =
                definitionService.create(
                        userId,
                        new CreateDefinitionCommand(
                                "wfblank",
                                Optional.empty(),
                                List.of(
                                        new RuntimeTraceRegressionSuiteDefinitionEntrySpec.ByConversation(
                                                conv, Optional.empty(), Optional.empty(), Optional.of("   ")))));
        String wf =
                jdbcTemplate.queryForObject(
                        "SELECT workflow_name FROM runtime_trace_regression_suite_definition_entry WHERE definition_id = ?",
                        String.class,
                        id);
        assertThat(wf).isNull();
        RuntimeTraceRegressionSuiteRequest req = definitionService.materializeToSuiteRequest(id, userId);
        var byConv = (RuntimeTraceRegressionSuiteEntry.ByConversation) req.entries().getFirst();
        assertThat(byConv.workflowName()).isEmpty();
    }

    @Test
    void duplicateNameOnCreate_throwsIllegalState() {
        UUID userId = insertUser();
        definitionService.create(
                userId,
                new CreateDefinitionCommand(
                        "dup", Optional.empty(), List.of(new RuntimeTraceRegressionSuiteDefinitionEntrySpec.ByTraceIds(List.of()))));
        assertThatThrownBy(
                        () ->
                                definitionService.create(
                                        userId,
                                        new CreateDefinitionCommand(
                                                "dup",
                                                Optional.empty(),
                                                List.of(new RuntimeTraceRegressionSuiteDefinitionEntrySpec.ByTraceIds(List.of())))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void renameToTakenName_throwsIllegalState() {
        UUID userId = insertUser();
        definitionService.create(
                userId,
                new CreateDefinitionCommand(
                        "taken", Optional.empty(), List.of(new RuntimeTraceRegressionSuiteDefinitionEntrySpec.ByTraceIds(List.of()))));
        UUID second =
                definitionService.create(
                        userId,
                        new CreateDefinitionCommand(
                                "other", Optional.empty(), List.of(new RuntimeTraceRegressionSuiteDefinitionEntrySpec.ByTraceIds(List.of()))));
        var upd =
                new UpdateDefinitionCommand(
                        "taken",
                        Optional.empty(),
                        List.of(new RuntimeTraceRegressionSuiteDefinitionEntrySpec.ByTraceIds(List.of())));
        assertThatThrownBy(() -> definitionService.update(second, userId, upd)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void delete_removesAllChildRows() {
        UUID userId = insertUser();
        UUID id =
                definitionService.create(
                        userId,
                        new CreateDefinitionCommand(
                                "del",
                                Optional.empty(),
                                List.of(new RuntimeTraceRegressionSuiteDefinitionEntrySpec.ByTraceIds(List.of(UUID.randomUUID())))));
        definitionService.delete(id, userId);
        Integer entries =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*)::int FROM runtime_trace_regression_suite_definition_entry WHERE definition_id = ?",
                        Integer.class,
                        id);
        Integer traces =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*)::int FROM runtime_trace_regression_suite_definition_entry_trace t "
                                + "JOIN runtime_trace_regression_suite_definition_entry e ON t.entry_id = e.id "
                                + "WHERE e.definition_id = ?",
                        Integer.class,
                        id);
        assertThat(entries).isZero();
        assertThat(traces).isZero();
    }
}

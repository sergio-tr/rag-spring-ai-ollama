package com.uniovi.rag.infrastructure.persistence.traceregressionsuitedefinition;

import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.CreateDefinitionCommand;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionEntrySnapshot;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionEntrySpec;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionSnapshot;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.UpdateDefinitionCommand;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteEntry;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Maps P33 persistence entities to domain read models and P30 suite requests — one direction per call site.
 */
@Component
public class RuntimeTraceRegressionSuiteDefinitionPersistenceMapper {

    private static final short SCHEMA_VERSION_INSERT = 1;

    public RuntimeTraceRegressionSuiteDefinitionEntity newDefinitionForInsert(
            UUID id, UUID userId, String normalizedName, String descriptionOrNull, Instant now) {
        RuntimeTraceRegressionSuiteDefinitionEntity e = new RuntimeTraceRegressionSuiteDefinitionEntity();
        e.setId(id);
        e.setUserId(userId);
        e.setName(normalizedName);
        e.setDescription(descriptionOrNull);
        e.setSchemaVersion(SCHEMA_VERSION_INSERT);
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        return e;
    }

    public void applyDefinitionUpdate(
            RuntimeTraceRegressionSuiteDefinitionEntity def, String normalizedName, String descriptionOrNull, Instant now) {
        def.setName(normalizedName);
        def.setDescription(descriptionOrNull);
        def.setUpdatedAt(now);
    }

    public RuntimeTraceRegressionSuiteDefinitionSnapshot toSnapshot(
            RuntimeTraceRegressionSuiteDefinitionEntity def,
            List<RuntimeTraceRegressionSuiteDefinitionEntryEntity> entriesOrdered,
            Map<UUID, List<UUID>> traceIdsByEntryId) {
        List<RuntimeTraceRegressionSuiteDefinitionEntrySnapshot> snapshots = new ArrayList<>(entriesOrdered.size());
        for (RuntimeTraceRegressionSuiteDefinitionEntryEntity row : entriesOrdered) {
            snapshots.add(toEntrySnapshot(row, traceIdsByEntryId));
        }
        return new RuntimeTraceRegressionSuiteDefinitionSnapshot(
                def.getId(),
                def.getName(),
                def.getDescription(),
                (int) def.getSchemaVersion(),
                def.getCreatedAt(),
                def.getUpdatedAt(),
                snapshots);
    }

    private RuntimeTraceRegressionSuiteDefinitionEntrySnapshot toEntrySnapshot(
            RuntimeTraceRegressionSuiteDefinitionEntryEntity row, Map<UUID, List<UUID>> traceIdsByEntryId) {
        if (SuiteDefinitionEntryKindColumn.BY_TRACE_IDS.equals(row.getEntryKind())) {
            List<UUID> ids = traceIdsByEntryId.getOrDefault(row.getId(), List.of());
            return new RuntimeTraceRegressionSuiteDefinitionEntrySnapshot.ByTraceIds(ids);
        }
        if (SuiteDefinitionEntryKindColumn.BY_CONVERSATION.equals(row.getEntryKind())) {
            return new RuntimeTraceRegressionSuiteDefinitionEntrySnapshot.ByConversation(
                    row.getConversationId(), row.getCreatedAtFrom(), row.getCreatedAtTo(), row.getWorkflowName());
        }
        throw new IllegalStateException("unknown entry_kind: " + row.getEntryKind());
    }

    public RuntimeTraceRegressionSuiteRequest toSuiteRequest(
            UUID userId,
            List<RuntimeTraceRegressionSuiteDefinitionEntryEntity> entriesOrdered,
            Map<UUID, List<UUID>> traceIdsByEntryId) {
        List<RuntimeTraceRegressionSuiteEntry> suiteEntries = new ArrayList<>(entriesOrdered.size());
        for (RuntimeTraceRegressionSuiteDefinitionEntryEntity row : entriesOrdered) {
            suiteEntries.add(toSuiteEntry(row, traceIdsByEntryId));
        }
        return new RuntimeTraceRegressionSuiteRequest(userId, suiteEntries);
    }

    private static RuntimeTraceRegressionSuiteEntry toSuiteEntry(
            RuntimeTraceRegressionSuiteDefinitionEntryEntity row, Map<UUID, List<UUID>> traceIdsByEntryId) {
        if (SuiteDefinitionEntryKindColumn.BY_TRACE_IDS.equals(row.getEntryKind())) {
            return new RuntimeTraceRegressionSuiteEntry.ByTraceIds(traceIdsByEntryId.getOrDefault(row.getId(), List.of()));
        }
        if (SuiteDefinitionEntryKindColumn.BY_CONVERSATION.equals(row.getEntryKind())) {
            return new RuntimeTraceRegressionSuiteEntry.ByConversation(
                    row.getConversationId(),
                    Optional.ofNullable(row.getCreatedAtFrom()),
                    Optional.ofNullable(row.getCreatedAtTo()),
                    workflowOptionalFromDb(row.getWorkflowName()));
        }
        throw new IllegalStateException("unknown entry_kind: " + row.getEntryKind());
    }

    private static Optional<String> workflowOptionalFromDb(String workflowName) {
        if (workflowName == null || workflowName.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(workflowName);
    }

    public void insertEntriesFromCommand(
            RuntimeTraceRegressionSuiteDefinitionEntity definition,
            CreateDefinitionCommand command,
            RuntimeTraceRegressionSuiteDefinitionEntryRepository entryRepository,
            RuntimeTraceRegressionSuiteDefinitionEntryTraceRepository traceRepository) {
        insertEntriesFromSpecs(definition, command.entries(), entryRepository, traceRepository);
    }

    public void insertEntriesFromCommand(
            RuntimeTraceRegressionSuiteDefinitionEntity definition,
            UpdateDefinitionCommand command,
            RuntimeTraceRegressionSuiteDefinitionEntryRepository entryRepository,
            RuntimeTraceRegressionSuiteDefinitionEntryTraceRepository traceRepository) {
        insertEntriesFromSpecs(definition, command.entries(), entryRepository, traceRepository);
    }

    private void insertEntriesFromSpecs(
            RuntimeTraceRegressionSuiteDefinitionEntity definition,
            List<RuntimeTraceRegressionSuiteDefinitionEntrySpec> specs,
            RuntimeTraceRegressionSuiteDefinitionEntryRepository entryRepository,
            RuntimeTraceRegressionSuiteDefinitionEntryTraceRepository traceRepository) {
        for (int i = 0; i < specs.size(); i++) {
            RuntimeTraceRegressionSuiteDefinitionEntrySpec spec = specs.get(i);
            RuntimeTraceRegressionSuiteDefinitionEntryEntity entryRow = newEntryEntity(definition, (short) i, spec);
            // Keep INSERT ordering stable: entry must exist before inserting traces referencing it.
            entryRow = entryRepository.save(entryRow);
            entryRepository.flush();
            if (spec instanceof RuntimeTraceRegressionSuiteDefinitionEntrySpec.ByTraceIds by) {
                short tp = 0;
                for (UUID traceId : by.traceIds()) {
                    RuntimeTraceRegressionSuiteDefinitionEntryTraceEntity traceRow = newTraceEntity(entryRow, tp++, traceId);
                    traceRepository.save(traceRow);
                }
            }
        }
    }

    private static RuntimeTraceRegressionSuiteDefinitionEntryEntity newEntryEntity(
            RuntimeTraceRegressionSuiteDefinitionEntity definition, short position, RuntimeTraceRegressionSuiteDefinitionEntrySpec spec) {
        RuntimeTraceRegressionSuiteDefinitionEntryEntity row = new RuntimeTraceRegressionSuiteDefinitionEntryEntity();
        row.setId(UUID.randomUUID());
        row.setDefinition(definition);
        row.setPosition(position);
        switch (spec) {
            case RuntimeTraceRegressionSuiteDefinitionEntrySpec.ByTraceIds ignored -> {
                row.setEntryKind(SuiteDefinitionEntryKindColumn.BY_TRACE_IDS);
                row.setConversationId(null);
                row.setCreatedAtFrom(null);
                row.setCreatedAtTo(null);
                row.setWorkflowName(null);
            }
            case RuntimeTraceRegressionSuiteDefinitionEntrySpec.ByConversation by -> {
                row.setEntryKind(SuiteDefinitionEntryKindColumn.BY_CONVERSATION);
                row.setConversationId(by.conversationId());
                row.setCreatedAtFrom(by.createdAtFrom().orElse(null));
                row.setCreatedAtTo(by.createdAtTo().orElse(null));
                row.setWorkflowName(workflowForDatabase(by.workflowName()));
            }
        }
        return row;
    }

    private static String workflowForDatabase(Optional<String> workflowName) {
        if (workflowName == null || workflowName.isEmpty()) {
            return null;
        }
        String trimmed = workflowName.get().trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static RuntimeTraceRegressionSuiteDefinitionEntryTraceEntity newTraceEntity(
            RuntimeTraceRegressionSuiteDefinitionEntryEntity entry, short position, UUID traceId) {
        RuntimeTraceRegressionSuiteDefinitionEntryTraceEntity row = new RuntimeTraceRegressionSuiteDefinitionEntryTraceEntity();
        row.setId(UUID.randomUUID());
        row.setEntry(entry);
        row.setPosition(position);
        row.setTraceId(traceId);
        return row;
    }
}

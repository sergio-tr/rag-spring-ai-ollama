package com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition;

import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionEntrySnapshot;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionSnapshot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record RuntimeTraceRegressionSuiteDefinitionDetailDto(
        UUID id,
        String name,
        String description,
        int schemaVersion,
        Instant createdAt,
        Instant updatedAt,
        List<RuntimeTraceRegressionSuiteDefinitionEntryDto> entries) {

    public RuntimeTraceRegressionSuiteDefinitionDetailDto {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public static RuntimeTraceRegressionSuiteDefinitionDetailDto fromSnapshot(RuntimeTraceRegressionSuiteDefinitionSnapshot snapshot) {
        List<RuntimeTraceRegressionSuiteDefinitionEntryDto> entryDtos = new ArrayList<>(snapshot.entries().size());
        for (RuntimeTraceRegressionSuiteDefinitionEntrySnapshot e : snapshot.entries()) {
            entryDtos.add(entryFromSnapshot(e));
        }
        return new RuntimeTraceRegressionSuiteDefinitionDetailDto(
                snapshot.id(),
                snapshot.name(),
                snapshot.description(),
                snapshot.schemaVersion(),
                snapshot.createdAt(),
                snapshot.updatedAt(),
                entryDtos);
    }

    private static RuntimeTraceRegressionSuiteDefinitionEntryDto entryFromSnapshot(
            RuntimeTraceRegressionSuiteDefinitionEntrySnapshot snap) {
        return switch (snap) {
            case RuntimeTraceRegressionSuiteDefinitionEntrySnapshot.ByTraceIds b ->
                    new RuntimeTraceRegressionSuiteDefinitionByTraceIdsEntryDto(b.traceIds());
            case RuntimeTraceRegressionSuiteDefinitionEntrySnapshot.ByConversation c ->
                    new RuntimeTraceRegressionSuiteDefinitionByConversationEntryDto(
                            c.conversationId(), c.createdAtFrom(), c.createdAtTo(), c.workflowName());
        };
    }
}

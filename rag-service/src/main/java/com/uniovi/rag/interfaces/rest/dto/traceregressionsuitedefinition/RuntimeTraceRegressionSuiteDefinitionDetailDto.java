package com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition;

import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.CreateDefinitionCommand;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionEntrySnapshot;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionEntrySpec;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionSnapshot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

    /**
     * Builds a {@link CreateDefinitionCommand} from logical definition fields only ({@code name}, {@code description},
     * {@code entries}). Does not use {@code id}, {@code schemaVersion}, {@code createdAt}, or {@code updatedAt}.
     */
    public CreateDefinitionCommand toCreateDefinitionCommand() {
        if (name() == null) {
            throw new IllegalArgumentException("name is required");
        }
        String trimmedName = name().trim();
        Optional<String> descriptionOptional = descriptionOptionalForCommand(description());
        List<RuntimeTraceRegressionSuiteDefinitionEntrySpec> specs = new ArrayList<>(entries().size());
        for (RuntimeTraceRegressionSuiteDefinitionEntryDto e : entries()) {
            if (e == null) {
                throw new IllegalArgumentException("entry must not be null");
            }
            specs.add(entryDtoToSpec(e));
        }
        return new CreateDefinitionCommand(trimmedName, descriptionOptional, specs);
    }

    private static Optional<String> descriptionOptionalForCommand(String description) {
        if (description == null) {
            return Optional.empty();
        }
        String t = description.trim();
        if (t.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(t);
    }

    private static RuntimeTraceRegressionSuiteDefinitionEntrySpec entryDtoToSpec(RuntimeTraceRegressionSuiteDefinitionEntryDto e) {
        return switch (e) {
            case RuntimeTraceRegressionSuiteDefinitionByTraceIdsEntryDto t ->
                    new RuntimeTraceRegressionSuiteDefinitionEntrySpec.ByTraceIds(t.getTraceIds());
            case RuntimeTraceRegressionSuiteDefinitionByConversationEntryDto c -> {
                Optional<Instant> from = Optional.ofNullable(c.getCreatedAtFrom());
                Optional<Instant> to = Optional.ofNullable(c.getCreatedAtTo());
                String wf = c.getWorkflowName();
                Optional<String> workflowNameOptional =
                        wf == null || wf.isBlank() ? Optional.empty() : Optional.of(wf.trim());
                yield new RuntimeTraceRegressionSuiteDefinitionEntrySpec.ByConversation(
                        c.getConversationId(), from, to, workflowNameOptional);
            }
            default -> throw new IllegalArgumentException("unsupported entry shape");
        };
    }
}

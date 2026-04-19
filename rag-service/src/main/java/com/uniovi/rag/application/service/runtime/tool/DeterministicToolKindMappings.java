package com.uniovi.rag.application.service.runtime.tool;

import com.uniovi.rag.configuration.ToolDescriptor;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;

import java.util.Optional;

public final class DeterministicToolKindMappings {

    private DeterministicToolKindMappings() {
    }

    public static QueryType toQueryType(DeterministicToolKind kind) {
        return kind.toQueryType();
    }

    /**
     * Resolves the model-returned tool name from {@link ToolDescriptor} / {@code @Tool} (e.g. {@code countDocuments}),
     * with fallback to {@link DeterministicToolKind} constant names for backward compatibility.
     */
    public static Optional<DeterministicToolKind> fromDeclaredToolName(String toolCallName) {
        if (toolCallName == null || toolCallName.isBlank()) {
            return Optional.empty();
        }
        for (DeterministicToolKind k : DeterministicToolKind.values()) {
            if (ToolDescriptor.getName(k.toQueryType()).equals(toolCallName)) {
                return Optional.of(k);
            }
        }
        try {
            return Optional.of(DeterministicToolKind.valueOf(toolCallName));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}

package com.uniovi.rag.domain.runtime.traceregressionsuitedefinition;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Domain validation for suite definition commands (P33 §12) — invoked by the application service before mapping to JPA.
 */
public final class RuntimeTraceRegressionSuiteDefinitionValidation {

    public static final int MIN_ENTRIES = 1;
    public static final int MAX_ENTRIES = 20;
    public static final int MAX_TRACE_IDS_PER_ENTRY = 50;

    private RuntimeTraceRegressionSuiteDefinitionValidation() {}

    public static void validateUserId(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
    }

    public static String normalizeAndValidateName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("name must not be null");
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (trimmed.length() > 256) {
            throw new IllegalArgumentException("name exceeds 256 characters after trim");
        }
        return trimmed;
    }

    /** Returns normalized description to persist, or empty if absent/blank after trim. */
    public static Optional<String> normalizeDescription(Optional<String> description) {
        if (description.isEmpty()) {
            return Optional.empty();
        }
        String trimmed = description.get().trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        if (trimmed.length() > 2048) {
            throw new IllegalArgumentException("description exceeds 2048 characters after trim");
        }
        return Optional.of(trimmed);
    }

    public static void validateEntryList(List<RuntimeTraceRegressionSuiteDefinitionEntrySpec> entries) {
        Objects.requireNonNull(entries, "entries");
        int n = entries.size();
        if (n < MIN_ENTRIES || n > MAX_ENTRIES) {
            throw new IllegalArgumentException("entry count must be between " + MIN_ENTRIES + " and " + MAX_ENTRIES + ", got " + n);
        }
        for (int i = 0; i < n; i++) {
            validateEntrySpec(entries.get(i), i);
        }
    }

    private static void validateEntrySpec(RuntimeTraceRegressionSuiteDefinitionEntrySpec spec, int indexInList) {
        switch (spec) {
            case RuntimeTraceRegressionSuiteDefinitionEntrySpec.ByTraceIds by ->
                    validateByTraceIdsEntry(by.traceIds(), indexInList);
            case RuntimeTraceRegressionSuiteDefinitionEntrySpec.ByConversation by ->
                    validateByConversationEntry(by, indexInList);
        }
    }

    private static void validateByTraceIdsEntry(List<UUID> ids, int indexInList) {
        if (ids.size() > MAX_TRACE_IDS_PER_ENTRY) {
            throw new IllegalArgumentException(
                    "BY_TRACE_IDS entry at index " + indexInList + " has more than " + MAX_TRACE_IDS_PER_ENTRY + " trace ids");
        }
        Set<UUID> seen = new HashSet<>();
        for (UUID id : ids) {
            if (id == null) {
                throw new IllegalArgumentException("null trace id in BY_TRACE_IDS entry at index " + indexInList);
            }
            if (!seen.add(id)) {
                throw new IllegalArgumentException("duplicate trace id in BY_TRACE_IDS entry at index " + indexInList);
            }
        }
    }

    private static void validateByConversationEntry(
            RuntimeTraceRegressionSuiteDefinitionEntrySpec.ByConversation by, int indexInList) {
        if (by.conversationId() == null) {
            throw new IllegalArgumentException("BY_CONVERSATION entry at index " + indexInList + " requires conversationId");
        }
        by.workflowName()
                .ifPresent(
                        w -> {
                            String t = w.trim();
                            if (!t.isEmpty() && t.length() > 256) {
                                throw new IllegalArgumentException(
                                        "workflow name exceeds 256 characters after trim at entry index " + indexInList);
                            }
                        });
    }
}

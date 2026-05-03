package com.uniovi.rag.interfaces.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.service.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteService;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteEntry;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteRequest;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuite.RuntimeTraceRegressionSuiteByConversationEntryRequestDto;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuite.RuntimeTraceRegressionSuiteByTraceIdsEntryRequestDto;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuite.RuntimeTraceRegressionSuiteConversationBatchSpecDto;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuite.RuntimeTraceRegressionSuiteConversationExecuteRequestDto;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuite.RuntimeTraceRegressionSuiteExecuteRequestDto;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuite.RuntimeTraceRegressionSuiteEntryRequestDto;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Shared P31/P32 validation and domain mapping for regression-suite JSON bodies (strict {@link ObjectMapper}).
 */
public final class RuntimeTraceRegressionSuiteHttpAdapter {

    /** Same cap as P24 {@code RuntimeTraceReplayComparisonBatchService#MAX_RAW_TRACE_IDS} (BY_TRACE_IDS raw list). */
    public static final int MAX_RAW_TRACE_IDS_PER_ENTRY = 50;

    private static final String KIND_BY_TRACE_IDS = "BY_TRACE_IDS";
    private static final String KIND_BY_CONVERSATION = "BY_CONVERSATION";

    private RuntimeTraceRegressionSuiteHttpAdapter() {}

    /** Validated explicit-suite request: domain model + accepted body for export manifest {@code scope}. */
    public record ValidatedExplicit(
            RuntimeTraceRegressionSuiteRequest domainRequest, RuntimeTraceRegressionSuiteExecuteRequestDto acceptedBody) {}

    /** Validated conversation-scoped suite: domain model + path id + accepted body for manifest {@code scope}. */
    public record ValidatedConversationScoped(
            RuntimeTraceRegressionSuiteRequest domainRequest,
            UUID pathConversationId,
            RuntimeTraceRegressionSuiteConversationExecuteRequestDto acceptedBody) {}

    /**
     * Parses and validates route-1 body; empty if HTTP 400.
     */
    public static Optional<ValidatedExplicit> parseExplicit(String body, ObjectMapper strictMapper, UUID userId) {
        if (body == null || body.isBlank() || userId == null) {
            return Optional.empty();
        }
        final RuntimeTraceRegressionSuiteExecuteRequestDto dto;
        try {
            dto = strictMapper.readValue(body, RuntimeTraceRegressionSuiteExecuteRequestDto.class);
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
        if (dto.entries() == null) {
            return Optional.empty();
        }
        if (dto.entries().size() > RuntimeTraceRegressionSuiteService.MAX_SUITE_ENTRIES) {
            return Optional.empty();
        }
        for (RuntimeTraceRegressionSuiteEntryRequestDto e : dto.entries()) {
            if (e == null) {
                return Optional.empty();
            }
        }
        if (!validateExplicitPolymorphicEntries(dto.entries())) {
            return Optional.empty();
        }
        List<RuntimeTraceRegressionSuiteEntry> domainEntries = mapExplicitEntries(dto.entries());
        return Optional.of(
                new ValidatedExplicit(
                        new RuntimeTraceRegressionSuiteRequest(userId, domainEntries), dto));
    }

    /**
     * Parses and validates route-2 body + path conversation id; empty if HTTP 400.
     */
    public static Optional<ValidatedConversationScoped> parseConversationScoped(
            String conversationIdRaw, String body, ObjectMapper strictMapper, UUID userId) {
        if (body == null || body.isBlank() || userId == null) {
            return Optional.empty();
        }
        final UUID pathConversationId;
        try {
            pathConversationId = UUID.fromString(conversationIdRaw);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        final RuntimeTraceRegressionSuiteConversationExecuteRequestDto dto;
        try {
            dto = strictMapper.readValue(body, RuntimeTraceRegressionSuiteConversationExecuteRequestDto.class);
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
        if (dto.entries() == null) {
            return Optional.empty();
        }
        if (dto.entries().size() > RuntimeTraceRegressionSuiteService.MAX_SUITE_ENTRIES) {
            return Optional.empty();
        }
        for (RuntimeTraceRegressionSuiteConversationBatchSpecDto row : dto.entries()) {
            if (row == null) {
                return Optional.empty();
            }
        }
        List<RuntimeTraceRegressionSuiteEntry> domainEntries = mapConversationSpecs(pathConversationId, dto.entries());
        return Optional.of(
                new ValidatedConversationScoped(
                        new RuntimeTraceRegressionSuiteRequest(userId, domainEntries),
                        pathConversationId,
                        dto));
    }

    public static boolean validateExplicitPolymorphicEntries(List<RuntimeTraceRegressionSuiteEntryRequestDto> entries) {
        for (RuntimeTraceRegressionSuiteEntryRequestDto e : entries) {
            switch (e) {
                case RuntimeTraceRegressionSuiteByTraceIdsEntryRequestDto t -> {
                    if (t.kind() == null || !KIND_BY_TRACE_IDS.equals(t.kind())) {
                        return false;
                    }
                    if (t.traceIds() == null) {
                        return false;
                    }
                    if (t.traceIds().size() > MAX_RAW_TRACE_IDS_PER_ENTRY) {
                        return false;
                    }
                    for (UUID id : t.traceIds()) {
                        if (id == null) {
                            return false;
                        }
                    }
                }
                case RuntimeTraceRegressionSuiteByConversationEntryRequestDto c -> {
                    if (c.kind() == null || !KIND_BY_CONVERSATION.equals(c.kind())) {
                        return false;
                    }
                    if (c.conversationId() == null) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public static List<RuntimeTraceRegressionSuiteEntry> mapExplicitEntries(
            List<RuntimeTraceRegressionSuiteEntryRequestDto> entries) {
        List<RuntimeTraceRegressionSuiteEntry> out = new ArrayList<>(entries.size());
        for (RuntimeTraceRegressionSuiteEntryRequestDto e : entries) {
            switch (e) {
                case RuntimeTraceRegressionSuiteByTraceIdsEntryRequestDto t ->
                        out.add(new RuntimeTraceRegressionSuiteEntry.ByTraceIds(t.traceIds()));
                case RuntimeTraceRegressionSuiteByConversationEntryRequestDto c ->
                        out.add(
                                new RuntimeTraceRegressionSuiteEntry.ByConversation(
                                        c.conversationId(),
                                        Optional.ofNullable(c.createdAtFrom()),
                                        Optional.ofNullable(c.createdAtTo()),
                                        normalizeWorkflowName(c.workflowName())));
            }
        }
        return out;
    }

    public static List<RuntimeTraceRegressionSuiteEntry> mapConversationSpecs(
            UUID pathConversationId, List<RuntimeTraceRegressionSuiteConversationBatchSpecDto> specs) {
        List<RuntimeTraceRegressionSuiteEntry> out = new ArrayList<>(specs.size());
        for (RuntimeTraceRegressionSuiteConversationBatchSpecDto s : specs) {
            out.add(
                    new RuntimeTraceRegressionSuiteEntry.ByConversation(
                            pathConversationId,
                            Optional.ofNullable(s.createdAtFrom()),
                            Optional.ofNullable(s.createdAtTo()),
                            normalizeWorkflowName(s.workflowName())));
        }
        return out;
    }

    /**
     * §6.2: trim workflow; null or blank after trim → empty optional (same as P25/P28 batch paths).
     */
    public static Optional<String> normalizeWorkflowName(String workflowName) {
        if (workflowName == null) {
            return Optional.empty();
        }
        String trimmed = workflowName.trim();
        return trimmed.isEmpty() ? Optional.empty() : Optional.of(trimmed);
    }
}

package com.uniovi.rag.interfaces.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.service.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteService;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionService;
import com.uniovi.rag.configuration.RegressionSuiteDefinitionMutationJacksonConfiguration;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteEntry;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteOutcome;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteRequest;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteResult;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.CreateDefinitionCommand;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionEntrySpec;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.UpdateDefinitionCommand;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionSnapshot;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionUserSummary;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionByConversationEntryDto;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionByTraceIdsEntryDto;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionDetailDto;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionListResponseDto;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionSummaryDto;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionUpsertByConversationEntryRequestDto;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionUpsertByTraceIdsEntryRequestDto;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionUpsertEntryRequestDto;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionUpsertRequestDto;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuite.RuntimeTraceRegressionSuiteResponseDto;
import com.uniovi.rag.security.RagPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * P34: read-only GET routes; P35: POST/PUT/DELETE for definitions; P36: POST execute routes materialize via
 * {@link RuntimeTraceRegressionSuiteDefinitionService#materializeToSuiteRequest} then {@link RuntimeTraceRegressionSuiteService#execute} only.
 */
@RestController
@RequestMapping("${rag.api.product-base-path}")
public class RuntimeTraceRegressionSuiteDefinitionController {

    private static final int NAME_MIN_LEN = 1;
    private static final int NAME_MAX_LEN = 256;
    private static final int DESCRIPTION_MAX_LEN = 2048;
    private static final int ENTRIES_MIN = 1;
    private static final int ENTRIES_MAX = 20;
    private static final int TRACE_IDS_MAX = 50;

    private final RuntimeTraceRegressionSuiteDefinitionService definitionService;
    private final RuntimeTraceRegressionSuiteService suiteService;
    private final ObjectMapper strictMutationMapper;
    private final String productBasePath;

    public RuntimeTraceRegressionSuiteDefinitionController(
            RuntimeTraceRegressionSuiteDefinitionService definitionService,
            RuntimeTraceRegressionSuiteService suiteService,
            @Qualifier(RegressionSuiteDefinitionMutationJacksonConfiguration.DEFINITION_MUTATION_STRICT_OBJECT_MAPPER)
                    ObjectMapper strictMutationMapper,
            @Value("${rag.api.product-base-path}") String productBasePath) {
        this.definitionService = definitionService;
        this.suiteService = suiteService;
        this.strictMutationMapper = strictMutationMapper;
        this.productBasePath = productBasePath;
    }

    @PostMapping(value = "/runtime-trace-regression-suite-definitions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> createDefinition(
            @AuthenticationPrincipal RagPrincipal principal,
            @RequestBody(required = false) String body,
            HttpServletRequest request) {
        if (request.getQueryString() != null) {
            return ResponseEntity.badRequest().build();
        }
        if (body == null) {
            return ResponseEntity.badRequest().build();
        }
        String trimmedBody = body.trim();
        if (trimmedBody.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        RuntimeTraceRegressionSuiteDefinitionUpsertRequestDto dto;
        try {
            dto = strictMutationMapper.readValue(trimmedBody, RuntimeTraceRegressionSuiteDefinitionUpsertRequestDto.class);
        } catch (JsonProcessingException ex) {
            return ResponseEntity.badRequest().build();
        }
        if (!isUpsertAdapterValid(dto)) {
            return ResponseEntity.badRequest().build();
        }
        UUID userId = principal.userId();
        try {
            UUID createdId = definitionService.create(userId, toCreateCommand(dto));
            String location =
                    productBasePath + "/runtime-trace-regression-suite-definitions/" + createdId;
            return ResponseEntity.created(URI.create(location)).build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @PutMapping(
            value = "/runtime-trace-regression-suite-definitions/{definitionId}",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> updateDefinition(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable("definitionId") String definitionIdRaw,
            @RequestBody(required = false) String body,
            HttpServletRequest request) {
        if (request.getQueryString() != null) {
            return ResponseEntity.badRequest().build();
        }
        Optional<UUID> definitionId = parseUuid(definitionIdRaw);
        if (definitionId.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        if (body == null) {
            return ResponseEntity.badRequest().build();
        }
        String trimmedBody = body.trim();
        if (trimmedBody.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        RuntimeTraceRegressionSuiteDefinitionUpsertRequestDto dto;
        try {
            dto = strictMutationMapper.readValue(trimmedBody, RuntimeTraceRegressionSuiteDefinitionUpsertRequestDto.class);
        } catch (JsonProcessingException ex) {
            return ResponseEntity.badRequest().build();
        }
        if (!isUpsertAdapterValid(dto)) {
            return ResponseEntity.badRequest().build();
        }
        UUID userId = principal.userId();
        try {
            definitionService.update(definitionId.get(), userId, toUpdateCommand(dto));
            return ResponseEntity.noContent().build();
        } catch (NotFoundException ex) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @DeleteMapping("/runtime-trace-regression-suite-definitions/{definitionId}")
    public ResponseEntity<Void> deleteDefinition(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable("definitionId") String definitionIdRaw,
            HttpServletRequest request) {
        if (request.getQueryString() != null) {
            return ResponseEntity.badRequest().build();
        }
        Optional<UUID> definitionId = parseUuid(definitionIdRaw);
        if (definitionId.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        UUID userId = principal.userId();
        try {
            definitionService.delete(definitionId.get(), userId);
            return ResponseEntity.noContent().build();
        } catch (NotFoundException ex) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/runtime-trace-regression-suite-definitions/{definitionId}/execute")
    public ResponseEntity<RuntimeTraceRegressionSuiteResponseDto> executeDefinitionFromSaved(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable("definitionId") String definitionIdRaw,
            @RequestBody(required = false) String body,
            HttpServletRequest request) {
        if (request.getQueryString() != null) {
            return ResponseEntity.badRequest().build();
        }
        Optional<UUID> definitionIdOpt = parseUuid(definitionIdRaw);
        if (definitionIdOpt.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        if (!isExecuteBodyAllowed(body)) {
            return ResponseEntity.badRequest().build();
        }
        UUID userId = principal.userId();
        UUID definitionId = definitionIdOpt.get();
        RuntimeTraceRegressionSuiteRequest req;
        try {
            req = definitionService.materializeToSuiteRequest(definitionId, userId);
        } catch (NotFoundException ex) {
            return ResponseEntity.notFound().build();
        }
        RuntimeTraceRegressionSuiteResult result = suiteService.execute(req);
        return respondDefinitionExecution(result);
    }

    @PostMapping("/conversations/{conversationId}/runtime-trace-regression-suite-definitions/{definitionId}/execute")
    public ResponseEntity<RuntimeTraceRegressionSuiteResponseDto> executeDefinitionFromSavedConversationScoped(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable("conversationId") String conversationIdRaw,
            @PathVariable("definitionId") String definitionIdRaw,
            @RequestBody(required = false) String body,
            HttpServletRequest request) {
        if (request.getQueryString() != null) {
            return ResponseEntity.badRequest().build();
        }
        Optional<UUID> definitionIdOpt = parseUuid(definitionIdRaw);
        Optional<UUID> conversationIdOpt = parseUuid(conversationIdRaw);
        if (definitionIdOpt.isEmpty() || conversationIdOpt.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        if (!isExecuteBodyAllowed(body)) {
            return ResponseEntity.badRequest().build();
        }
        UUID userId = principal.userId();
        UUID definitionId = definitionIdOpt.get();
        UUID conversationId = conversationIdOpt.get();
        RuntimeTraceRegressionSuiteRequest req;
        try {
            req = definitionService.materializeToSuiteRequest(definitionId, userId);
        } catch (NotFoundException ex) {
            return ResponseEntity.notFound().build();
        }
        if (conversationScopedGuardFails(conversationId, req)) {
            return ResponseEntity.notFound().build();
        }
        RuntimeTraceRegressionSuiteResult result = suiteService.execute(req);
        return respondDefinitionExecution(result);
    }

    @GetMapping("/runtime-trace-regression-suite-definitions")
    public ResponseEntity<RuntimeTraceRegressionSuiteDefinitionListResponseDto> listDefinitions(
            @AuthenticationPrincipal RagPrincipal principal, HttpServletRequest request) {
        if (request.getQueryString() != null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(toListResponse(definitionService.listSummariesForUser(principal.userId())));
    }

    @GetMapping("/runtime-trace-regression-suite-definitions/{definitionId}")
    public ResponseEntity<RuntimeTraceRegressionSuiteDefinitionDetailDto> getDefinition(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable("definitionId") String definitionIdRaw,
            HttpServletRequest request) {
        if (request.getQueryString() != null) {
            return ResponseEntity.badRequest().build();
        }
        Optional<UUID> definitionId = parseUuid(definitionIdRaw);
        if (definitionId.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        Optional<RuntimeTraceRegressionSuiteDefinitionSnapshot> snap =
                definitionService.loadByIdForUser(definitionId.get(), principal.userId());
        if (snap.isEmpty()) {
            throw new NotFoundException("definition not found");
        }
        return ResponseEntity.ok(RuntimeTraceRegressionSuiteDefinitionDetailDto.fromSnapshot(snap.get()));
    }

    private static boolean isExecuteBodyAllowed(String body) {
        if (body == null) {
            return true;
        }
        return body.trim().isEmpty();
    }

    private static boolean conversationScopedGuardFails(UUID pathConversationId, RuntimeTraceRegressionSuiteRequest request) {
        boolean sawConversation = false;
        for (RuntimeTraceRegressionSuiteEntry e : request.entries()) {
            if (e instanceof RuntimeTraceRegressionSuiteEntry.ByConversation bc) {
                sawConversation = true;
                if (!pathConversationId.equals(bc.conversationId())) {
                    return true;
                }
            }
        }
        return !sawConversation;
    }

    private static ResponseEntity<RuntimeTraceRegressionSuiteResponseDto> respondDefinitionExecution(
            RuntimeTraceRegressionSuiteResult result) {
        if (result.suiteOutcome() == RuntimeTraceRegressionSuiteOutcome.NOT_ATTEMPTED) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(RuntimeTraceRegressionSuiteResponseDto.fromResult(result));
    }

    private static boolean isUpsertAdapterValid(RuntimeTraceRegressionSuiteDefinitionUpsertRequestDto dto) {
        List<RuntimeTraceRegressionSuiteDefinitionUpsertEntryRequestDto> entries = dto.entries();
        if (entries == null || entries.size() < ENTRIES_MIN || entries.size() > ENTRIES_MAX) {
            return false;
        }
        for (RuntimeTraceRegressionSuiteDefinitionUpsertEntryRequestDto e : entries) {
            if (e == null) {
                return false;
            }
        }

        String name = dto.name();
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        String trimmedName = name.trim();
        if (trimmedName.length() < NAME_MIN_LEN || trimmedName.length() > NAME_MAX_LEN) {
            return false;
        }

        String description = dto.description();
        if (description != null) {
            String dTrim = description.trim();
            if (!dTrim.isEmpty() && dTrim.length() > DESCRIPTION_MAX_LEN) {
                return false;
            }
        }

        for (RuntimeTraceRegressionSuiteDefinitionUpsertEntryRequestDto e : entries) {
            if (e instanceof RuntimeTraceRegressionSuiteDefinitionUpsertByTraceIdsEntryRequestDto w3) {
                List<UUID> traceIds = w3.getTraceIds();
                if (traceIds == null) {
                    return false;
                }
                if (traceIds.stream().anyMatch(Objects::isNull)) {
                    return false;
                }
                if (traceIds.size() > TRACE_IDS_MAX) {
                    return false;
                }
            } else if (e instanceof RuntimeTraceRegressionSuiteDefinitionUpsertByConversationEntryRequestDto w4) {
                if (w4.getConversationId() == null) {
                    return false;
                }
            } else {
                return false;
            }
        }
        return true;
    }

    private static CreateDefinitionCommand toCreateCommand(RuntimeTraceRegressionSuiteDefinitionUpsertRequestDto dto) {
        String trimmedName = dto.name().trim();
        Optional<String> descriptionOptional = descriptionOptionalFromDto(dto.description());
        List<RuntimeTraceRegressionSuiteDefinitionEntrySpec> entrySpecs = toEntrySpecs(dto.entries());
        return new CreateDefinitionCommand(trimmedName, descriptionOptional, entrySpecs);
    }

    private static UpdateDefinitionCommand toUpdateCommand(RuntimeTraceRegressionSuiteDefinitionUpsertRequestDto dto) {
        String trimmedName = dto.name().trim();
        Optional<String> descriptionOptional = descriptionOptionalFromDto(dto.description());
        List<RuntimeTraceRegressionSuiteDefinitionEntrySpec> entrySpecs = toEntrySpecs(dto.entries());
        return new UpdateDefinitionCommand(trimmedName, descriptionOptional, entrySpecs);
    }

    private static Optional<String> descriptionOptionalFromDto(String description) {
        if (description == null) {
            return Optional.empty();
        }
        String t = description.trim();
        if (t.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(t);
    }

    private static List<RuntimeTraceRegressionSuiteDefinitionEntrySpec> toEntrySpecs(
            List<RuntimeTraceRegressionSuiteDefinitionUpsertEntryRequestDto> entries) {
        List<RuntimeTraceRegressionSuiteDefinitionEntrySpec> specs = new ArrayList<>(entries.size());
        for (RuntimeTraceRegressionSuiteDefinitionUpsertEntryRequestDto e : entries) {
            if (e instanceof RuntimeTraceRegressionSuiteDefinitionUpsertByTraceIdsEntryRequestDto w3) {
                specs.add(new RuntimeTraceRegressionSuiteDefinitionEntrySpec.ByTraceIds(w3.getTraceIds()));
            } else if (e instanceof RuntimeTraceRegressionSuiteDefinitionUpsertByConversationEntryRequestDto w4) {
                Optional<Instant> from = Optional.ofNullable(w4.getCreatedAtFrom());
                Optional<Instant> to = Optional.ofNullable(w4.getCreatedAtTo());
                String wf = w4.getWorkflowName();
                Optional<String> workflowNameOptional =
                        wf == null || wf.isBlank() ? Optional.empty() : Optional.of(wf.trim());
                specs.add(
                        new RuntimeTraceRegressionSuiteDefinitionEntrySpec.ByConversation(
                                w4.getConversationId(), from, to, workflowNameOptional));
            }
        }
        return specs;
    }

    private static Optional<UUID> parseUuid(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw.trim()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static RuntimeTraceRegressionSuiteDefinitionListResponseDto toListResponse(
            List<RuntimeTraceRegressionSuiteDefinitionUserSummary> rows) {
        List<RuntimeTraceRegressionSuiteDefinitionSummaryDto> defs = new ArrayList<>(rows.size());
        for (RuntimeTraceRegressionSuiteDefinitionUserSummary s : rows) {
            defs.add(
                    new RuntimeTraceRegressionSuiteDefinitionSummaryDto(
                            s.definitionId(), s.name(), s.description(), s.entryCount(), s.createdAt(), s.updatedAt()));
        }
        return new RuntimeTraceRegressionSuiteDefinitionListResponseDto(defs);
    }

}

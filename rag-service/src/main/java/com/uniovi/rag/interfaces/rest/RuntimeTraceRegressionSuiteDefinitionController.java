package com.uniovi.rag.interfaces.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.service.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteService;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunPersistenceService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunexport.RuntimeTraceRegressionSuiteRunExportArtifact;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunexport.RuntimeTraceRegressionSuiteRunExportSizeExceededException;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunimport.RuntimeTraceRegressionSuiteRunImportRejectedException;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunimport.RuntimeTraceRegressionSuiteRunImportService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunimportpreview.RuntimeTraceRegressionSuiteRunImportPreviewRejectedException;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunimportpreview.RuntimeTraceRegressionSuiteRunImportPreviewService;
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
import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunSnapshot;
import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunSourceType;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionDetailDto;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionListResponseDto;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionSummaryDto;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionUpsertByConversationEntryRequestDto;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionUpsertByTraceIdsEntryRequestDto;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionUpsertEntryRequestDto;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionUpsertRequestDto;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuite.RuntimeTraceRegressionSuiteResponseDto;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunDetailDto;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunListResponseDto;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuiterunimportpreview.RuntimeTraceRegressionSuiteRunImportPreviewResponseDto;
import com.uniovi.rag.security.RagPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
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
 *
 * <p>P47: two {@code POST} routes persist a suite run after materialize + {@code execute} with {@link RuntimeTraceRegressionSuiteRunSourceType#SAVED_DEFINITION} -
 * orchestration only in this controller (no bridge {@code @Service}).
 *
 * <p>P50: two {@code GET} routes list/detail persisted runs for a saved definition - {@link RuntimeTraceRegressionSuiteDefinitionService#loadByIdForUser}
 * gate only, then {@link RuntimeTraceRegressionSuiteRunPersistenceService} definition-scoped reads (no {@code execute}, no {@code createRun}).
 *
 * <p>P52: one {@code DELETE} route removes a persisted run in definition context - same gate, then
 * {@link RuntimeTraceRegressionSuiteRunPersistenceService#deleteRunForUserAndDefinition} only (no global
 * {@link RuntimeTraceRegressionSuiteRunPersistenceService#deleteRunForUser(UUID, UUID)}).
 *
 * <p>P53–P55: definition-scoped run ZIP export / import / preview - gated routes delegate to the corresponding services exposed via
 * {@link DefinitionRunZipServiceBundle}.
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
    private final RuntimeTraceRegressionSuiteRunPersistenceService runPersistenceService;
    private final ObjectMapper strictMutationMapper;
    private final String productBasePath;
    private final DefinitionRunZipServiceBundle runZipServices;

    public RuntimeTraceRegressionSuiteDefinitionController(
            RuntimeTraceRegressionSuiteDefinitionService definitionService,
            RuntimeTraceRegressionSuiteService suiteService,
            RuntimeTraceRegressionSuiteRunPersistenceService runPersistenceService,
            @Qualifier(RegressionSuiteDefinitionMutationJacksonConfiguration.DEFINITION_MUTATION_STRICT_OBJECT_MAPPER)
                    ObjectMapper strictMutationMapper,
            @Value("${rag.api.product-base-path}") String productBasePath,
            DefinitionRunZipServiceBundle runZipServices) {
        this.definitionService = definitionService;
        this.suiteService = suiteService;
        this.runPersistenceService = runPersistenceService;
        this.strictMutationMapper = strictMutationMapper;
        this.productBasePath = productBasePath;
        this.runZipServices = runZipServices;
    }

    @PostMapping(value = "/runtime-trace-regression-suite-definitions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> createDefinition(
            @AuthenticationPrincipal RagPrincipal principal,
            @RequestBody(required = false) String body,
            HttpServletRequest request) {
        Optional<RuntimeTraceRegressionSuiteDefinitionUpsertRequestDto> dtoOpt =
                parseValidUpsertRequestBody(request, body);
        if (dtoOpt.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        UUID userId = principal.userId();
        try {
            UUID createdId = definitionService.create(userId, toCreateCommand(dtoOpt.get()));
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
        Optional<UUID> definitionId = parseUuid(definitionIdRaw);
        if (definitionId.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        Optional<RuntimeTraceRegressionSuiteDefinitionUpsertRequestDto> dtoOpt =
                parseValidUpsertRequestBody(request, body);
        if (dtoOpt.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        UUID userId = principal.userId();
        try {
            definitionService.update(definitionId.get(), userId, toUpdateCommand(dtoOpt.get()));
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
        Optional<RuntimeTraceRegressionSuiteRequest> reqOpt = materializeRequestForDefinition(definitionId, userId);
        if (reqOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        RuntimeTraceRegressionSuiteResult result = suiteService.execute(reqOpt.get());
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
        Optional<RuntimeTraceRegressionSuiteRequest> reqOpt = materializeRequestForDefinition(definitionId, userId);
        if (reqOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        RuntimeTraceRegressionSuiteRequest req = reqOpt.get();
        if (conversationScopedGuardFails(conversationId, req)) {
            return ResponseEntity.notFound().build();
        }
        RuntimeTraceRegressionSuiteResult result = suiteService.execute(req);
        return respondDefinitionExecution(result);
    }

    @PostMapping("/runtime-trace-regression-suite-definitions/{definitionId}/runs")
    public ResponseEntity<Void> createPersistedRunFromSavedDefinition(
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
        Optional<RuntimeTraceRegressionSuiteRequest> reqOpt = materializeRequestForDefinition(definitionId, userId);
        if (reqOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        RuntimeTraceRegressionSuiteResult result = suiteService.execute(reqOpt.get());
        return respondPersistedRunFromDefinition(userId, definitionId, result);
    }

    @PostMapping("/conversations/{conversationId}/runtime-trace-regression-suite-definitions/{definitionId}/runs")
    public ResponseEntity<Void> createPersistedRunFromSavedDefinitionConversationScoped(
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
        Optional<RuntimeTraceRegressionSuiteRequest> reqOpt = materializeRequestForDefinition(definitionId, userId);
        if (reqOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        RuntimeTraceRegressionSuiteRequest req = reqOpt.get();
        if (conversationScopedGuardFails(conversationId, req)) {
            return ResponseEntity.notFound().build();
        }
        RuntimeTraceRegressionSuiteResult result = suiteService.execute(req);
        return respondPersistedRunFromDefinition(userId, definitionId, result);
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

    @GetMapping("/runtime-trace-regression-suite-definitions/{definitionId}/runs")
    public ResponseEntity<RuntimeTraceRegressionSuiteRunListResponseDto> listRunsForDefinition(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable("definitionId") String definitionIdRaw,
            HttpServletRequest request) {
        if (request.getQueryString() != null) {
            return ResponseEntity.badRequest().build();
        }
        Optional<UUID> definitionIdOpt = parseUuid(definitionIdRaw);
        if (definitionIdOpt.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        UUID userId = principal.userId();
        UUID definitionId = definitionIdOpt.get();
        if (definitionService.loadByIdForUser(definitionId, userId).isEmpty()) {
            throw new NotFoundException("definition not found");
        }
        return ResponseEntity.ok(
                RuntimeTraceRegressionSuiteRunListResponseDto.fromSummaries(
                        runPersistenceService.listSummariesForUserAndDefinition(userId, definitionId)));
    }

    @GetMapping("/runtime-trace-regression-suite-definitions/{definitionId}/runs/{runId}")
    public ResponseEntity<RuntimeTraceRegressionSuiteRunDetailDto> getRunForDefinition(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable("definitionId") String definitionIdRaw,
            @PathVariable("runId") String runIdRaw,
            HttpServletRequest request) {
        if (request.getQueryString() != null) {
            return ResponseEntity.badRequest().build();
        }
        Optional<UUID> definitionIdOpt = parseUuid(definitionIdRaw);
        if (definitionIdOpt.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        Optional<UUID> runIdOpt = parseUuid(runIdRaw);
        if (runIdOpt.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        UUID userId = principal.userId();
        UUID definitionId = definitionIdOpt.get();
        UUID runId = runIdOpt.get();
        if (definitionService.loadByIdForUser(definitionId, userId).isEmpty()) {
            throw new NotFoundException("definition not found");
        }
        Optional<RuntimeTraceRegressionSuiteRunSnapshot> snap =
                runPersistenceService.loadByIdForUserAndDefinition(runId, userId, definitionId);
        if (snap.isEmpty()) {
            throw new NotFoundException("run not found");
        }
        return ResponseEntity.ok(RuntimeTraceRegressionSuiteRunDetailDto.fromSnapshot(snap.get()));
    }

    @GetMapping(
            value = "/runtime-trace-regression-suite-definitions/{definitionId}/runs/{runId}/export",
            produces = "application/zip")
    public ResponseEntity<byte[]> exportRunZipForDefinition(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable("definitionId") String definitionIdRaw,
            @PathVariable("runId") String runIdRaw,
            HttpServletRequest request) {
        if (request.getQueryString() != null) {
            return ResponseEntity.badRequest().build();
        }
        Optional<UUID> definitionIdOpt = parseUuid(definitionIdRaw);
        if (definitionIdOpt.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        Optional<UUID> runIdOpt = parseUuid(runIdRaw);
        if (runIdOpt.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        UUID userId = principal.userId();
        UUID definitionId = definitionIdOpt.get();
        UUID runId = runIdOpt.get();
        if (definitionService.loadByIdForUser(definitionId, userId).isEmpty()) {
            throw new NotFoundException("definition not found");
        }
        try {
            return toZipResponse(runZipServices.runExportService().exportRunZipForDefinition(runId, userId, definitionId));
        } catch (NotFoundException ex) {
            return ResponseEntity.notFound().build();
        } catch (RuntimeTraceRegressionSuiteRunExportSizeExceededException ex) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
        }
    }

    @PostMapping("/runtime-trace-regression-suite-definitions/{definitionId}/runs/import")
    public ResponseEntity<Void> importRunZipForDefinition(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable("definitionId") String definitionIdRaw,
            HttpServletRequest request) {
        if (request.getQueryString() != null) {
            return ResponseEntity.badRequest().build();
        }
        Optional<UUID> definitionIdOpt = parseUuid(definitionIdRaw);
        if (definitionIdOpt.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        UUID definitionId = definitionIdOpt.get();
        UUID userId = principal.userId();
        if (definitionService.loadByIdForUser(definitionId, userId).isEmpty()) {
            throw new NotFoundException("definition not found");
        }
        Optional<byte[]> bodyOpt =
                RuntimeTraceImportRequestSupport.readZipBody(
                        request, RuntimeTraceRegressionSuiteRunImportService.MAX_IMPORT_ZIP_BYTES);
        if (bodyOpt.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        byte[] body = bodyOpt.get();
        try {
            UUID createdId = runZipServices.runImportService().importRunZipForDefinition(body, userId, definitionId);
            String location = productBasePath + "/runtime-trace-regression-suite-runs/" + createdId;
            return ResponseEntity.created(URI.create(location)).build();
        } catch (RuntimeTraceRegressionSuiteRunImportRejectedException | IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/runtime-trace-regression-suite-definitions/{definitionId}/runs/import/preview")
    public ResponseEntity<RuntimeTraceRegressionSuiteRunImportPreviewResponseDto> previewImportZipForDefinition(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable("definitionId") String definitionIdRaw,
            HttpServletRequest request) {
        if (request.getQueryString() != null) {
            return ResponseEntity.badRequest().build();
        }
        Optional<UUID> definitionIdOpt = parseUuid(definitionIdRaw);
        if (definitionIdOpt.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        UUID definitionId = definitionIdOpt.get();
        UUID userId = principal.userId();
        if (definitionService.loadByIdForUser(definitionId, userId).isEmpty()) {
            throw new NotFoundException("definition not found");
        }
        Optional<byte[]> bodyOpt =
                RuntimeTraceImportRequestSupport.readZipBody(
                        request, RuntimeTraceRegressionSuiteRunImportPreviewService.MAX_PREVIEW_ZIP_BYTES);
        if (bodyOpt.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        byte[] body = bodyOpt.get();
        try {
            RuntimeTraceRegressionSuiteRunImportPreviewResponseDto dto =
                    runZipServices.runImportPreviewService().previewImportZipForDefinition(body, definitionId);
            return ResponseEntity.ok(dto);
        } catch (RuntimeTraceRegressionSuiteRunImportPreviewRejectedException | IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/runtime-trace-regression-suite-definitions/{definitionId}/runs/{runId}")
    public ResponseEntity<Void> deleteRunForDefinition(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable("definitionId") String definitionIdRaw,
            @PathVariable("runId") String runIdRaw,
            HttpServletRequest request) {
        if (request.getQueryString() != null) {
            return ResponseEntity.badRequest().build();
        }
        Optional<UUID> definitionIdOpt = parseUuid(definitionIdRaw);
        if (definitionIdOpt.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        Optional<UUID> runIdOpt = parseUuid(runIdRaw);
        if (runIdOpt.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        UUID userId = principal.userId();
        UUID definitionId = definitionIdOpt.get();
        UUID runId = runIdOpt.get();
        if (definitionService.loadByIdForUser(definitionId, userId).isEmpty()) {
            throw new NotFoundException("definition not found");
        }
        boolean deleted = runPersistenceService.deleteRunForUserAndDefinition(runId, userId, definitionId);
        if (deleted) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    private static boolean isExecuteBodyAllowed(String body) {
        if (body == null) {
            return true;
        }
        return body.trim().isEmpty();
    }

    private Optional<RuntimeTraceRegressionSuiteDefinitionUpsertRequestDto> parseValidUpsertRequestBody(
            HttpServletRequest request, String body) {
        if (request.getQueryString() != null || body == null) {
            return Optional.empty();
        }
        String trimmedBody = body.trim();
        if (trimmedBody.isEmpty()) {
            return Optional.empty();
        }
        try {
            RuntimeTraceRegressionSuiteDefinitionUpsertRequestDto dto =
                    strictMutationMapper.readValue(trimmedBody, RuntimeTraceRegressionSuiteDefinitionUpsertRequestDto.class);
            return isUpsertAdapterValid(dto) ? Optional.of(dto) : Optional.empty();
        } catch (JsonProcessingException ex) {
            return Optional.empty();
        }
    }

    private Optional<RuntimeTraceRegressionSuiteRequest> materializeRequestForDefinition(UUID definitionId, UUID userId) {
        try {
            return Optional.of(definitionService.materializeToSuiteRequest(definitionId, userId));
        } catch (NotFoundException ex) {
            return Optional.empty();
        }
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

    private ResponseEntity<Void> respondPersistedRunFromDefinition(
            UUID userId, UUID definitionId, RuntimeTraceRegressionSuiteResult result) {
        if (result.suiteOutcome() == RuntimeTraceRegressionSuiteOutcome.NOT_ATTEMPTED) {
            return ResponseEntity.badRequest().build();
        }
        UUID createdRunId =
                runPersistenceService.createRun(
                        userId,
                        RuntimeTraceRegressionSuiteRunSourceType.SAVED_DEFINITION,
                        Optional.of(definitionId),
                        result);
        String location = productBasePath + "/runtime-trace-regression-suite-runs/" + createdRunId;
        return ResponseEntity.created(URI.create(location)).build();
    }

    private static boolean isUpsertAdapterValid(RuntimeTraceRegressionSuiteDefinitionUpsertRequestDto dto) {
        List<RuntimeTraceRegressionSuiteDefinitionUpsertEntryRequestDto> entries = dto.entries();
        if (!upsertEntriesStructureOk(entries)) {
            return false;
        }
        if (!upsertNameOk(dto.name())) {
            return false;
        }
        if (!upsertDescriptionOk(dto.description())) {
            return false;
        }
        return entries.stream().allMatch(RuntimeTraceRegressionSuiteDefinitionController::upsertEntryPayloadOk);
    }

    private static boolean upsertEntriesStructureOk(List<RuntimeTraceRegressionSuiteDefinitionUpsertEntryRequestDto> entries) {
        if (entries == null || entries.size() < ENTRIES_MIN || entries.size() > ENTRIES_MAX) {
            return false;
        }
        return entries.stream().noneMatch(Objects::isNull);
    }

    private static boolean upsertNameOk(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        String trimmedName = name.trim();
        return trimmedName.length() >= NAME_MIN_LEN && trimmedName.length() <= NAME_MAX_LEN;
    }

    private static boolean upsertDescriptionOk(String description) {
        if (description == null) {
            return true;
        }
        String dTrim = description.trim();
        return dTrim.isEmpty() || dTrim.length() <= DESCRIPTION_MAX_LEN;
    }

    private static boolean upsertEntryPayloadOk(RuntimeTraceRegressionSuiteDefinitionUpsertEntryRequestDto e) {
        if (e instanceof RuntimeTraceRegressionSuiteDefinitionUpsertByTraceIdsEntryRequestDto w3) {
            List<UUID> traceIds = w3.getTraceIds();
            if (traceIds == null || traceIds.stream().anyMatch(Objects::isNull)) {
                return false;
            }
            return traceIds.size() <= TRACE_IDS_MAX;
        }
        if (e instanceof RuntimeTraceRegressionSuiteDefinitionUpsertByConversationEntryRequestDto w4) {
            return w4.getConversationId() != null;
        }
        return false;
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

    private static ResponseEntity<byte[]> toZipResponse(RuntimeTraceRegressionSuiteRunExportArtifact artifact) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + artifact.filename() + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, Long.toString(artifact.sizeBytes()))
                .contentType(MediaType.parseMediaType(artifact.mediaType()))
                .body(artifact.content());
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

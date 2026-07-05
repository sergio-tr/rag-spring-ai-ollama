package com.uniovi.rag.interfaces.rest.admin;

import com.uniovi.rag.application.service.admin.model.AdminModelsService;
import com.uniovi.rag.application.service.async.AsyncTaskService;
import com.uniovi.rag.configuration.RagApiPathProperties;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.infrastructure.llm.LlmProperties;
import com.uniovi.rag.interfaces.rest.admin.dto.AdminModelCheckRequest;
import com.uniovi.rag.interfaces.rest.admin.dto.AdminModelCheckResponse;
import com.uniovi.rag.interfaces.rest.admin.dto.AdminModelDeleteResponse;
import com.uniovi.rag.interfaces.rest.admin.dto.AdminModelEntryDto;
import com.uniovi.rag.interfaces.rest.admin.dto.AdminModelUpdateRequest;
import com.uniovi.rag.interfaces.rest.admin.dto.AdminModelUpsertRequest;
import com.uniovi.rag.interfaces.rest.admin.dto.PullOllamaModelRequest;
import com.uniovi.rag.interfaces.rest.dto.LabJobAcceptedDto;
import com.uniovi.rag.security.RagPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Product-scoped admin model endpoints (v5): check/pull + allowlist persistence.
 */
@RestController
@RequestMapping("${rag.api.product-base-path}/admin/models")
public class AdminModelsController {

    private final AdminModelsService adminModelsService;
    private final AsyncTaskService asyncTaskService;
    private final RagApiPathProperties apiPathProperties;
    private final LlmProperties llmProperties;

    public AdminModelsController(
            AdminModelsService adminModelsService,
            AsyncTaskService asyncTaskService,
            RagApiPathProperties apiPathProperties,
            LlmProperties llmProperties) {
        this.adminModelsService = adminModelsService;
        this.asyncTaskService = asyncTaskService;
        this.apiPathProperties = apiPathProperties;
        this.llmProperties = llmProperties;
    }

    @GetMapping
    public List<AdminModelEntryDto> list(@AuthenticationPrincipal RagPrincipal principal) {
        return adminModelsService.list();
    }

    @PostMapping("/check")
    public AdminModelCheckResponse check(
            @AuthenticationPrincipal RagPrincipal principal, @Valid @RequestBody AdminModelCheckRequest body) {
        return adminModelsService.check(body);
    }

    @PostMapping("/{id}/check")
    public AdminModelCheckResponse reprobe(
            @AuthenticationPrincipal RagPrincipal principal, @PathVariable UUID id) {
        return adminModelsService.reprobe(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminModelEntryDto upsert(
            @AuthenticationPrincipal RagPrincipal principal, @Valid @RequestBody AdminModelUpsertRequest body) {
        return adminModelsService.upsert(body);
    }

    @PutMapping("/{id}")
    public AdminModelEntryDto update(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody AdminModelUpdateRequest body) {
        return adminModelsService.update(id, body);
    }

    @DeleteMapping("/{id}")
    public AdminModelDeleteResponse delete(@AuthenticationPrincipal RagPrincipal principal, @PathVariable UUID id) {
        return adminModelsService.delete(id);
    }

    @PostMapping("/pull")
    public ResponseEntity<LabJobAcceptedDto> pull(
            @AuthenticationPrincipal RagPrincipal principal,
            @Valid @RequestBody PullOllamaModelRequest body) {
        if (llmProperties.getEffectiveDefaultChatProvider() != LlmProvider.OLLAMA_NATIVE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "PULL_ONLY_SUPPORTED_FOR_LOCAL_MODEL_SERVER");
        }
        UUID jobId = asyncTaskService.submitOllamaPull(principal.userId(), body.model().trim());
        String base = apiPathProperties.getProductBasePath() + "/lab/jobs/" + jobId;
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new LabJobAcceptedDto(jobId, "ACCEPTED", base, base + "/events"));
    }
}

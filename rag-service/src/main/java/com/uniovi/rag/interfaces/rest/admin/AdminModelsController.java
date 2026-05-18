package com.uniovi.rag.interfaces.rest.admin;

import com.uniovi.rag.configuration.RagApiPathProperties;
import com.uniovi.rag.interfaces.rest.admin.dto.AdminModelCheckRequest;
import com.uniovi.rag.interfaces.rest.admin.dto.AdminModelCheckResponse;
import com.uniovi.rag.interfaces.rest.admin.dto.AdminModelEntryDto;
import com.uniovi.rag.interfaces.rest.admin.dto.AdminModelUpsertRequest;
import com.uniovi.rag.interfaces.rest.admin.dto.PullOllamaModelRequest;
import com.uniovi.rag.interfaces.rest.dto.LabJobAcceptedDto;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.service.async.AsyncTaskService;
import com.uniovi.rag.service.admin.AdminModelsService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Product-scoped admin model endpoints (v5): check/pull + allowlist persistence.
 */
@RestController
@RequestMapping("${rag.api.product-base-path}/admin/models")
public class AdminModelsController {

    private final AdminModelsService adminModelsService;
    private final AsyncTaskService asyncTaskService;
    private final RagApiPathProperties apiPathProperties;

    public AdminModelsController(
            AdminModelsService adminModelsService,
            AsyncTaskService asyncTaskService,
            RagApiPathProperties apiPathProperties) {
        this.adminModelsService = adminModelsService;
        this.asyncTaskService = asyncTaskService;
        this.apiPathProperties = apiPathProperties;
    }

    @GetMapping
    public List<AdminModelEntryDto> list(@AuthenticationPrincipal RagPrincipal principal) {
        // Authentication/authorization is enforced by security config; controller stays thin.
        return adminModelsService.list();
    }

    @PostMapping("/check")
    public AdminModelCheckResponse check(@AuthenticationPrincipal RagPrincipal principal, @Valid @RequestBody AdminModelCheckRequest body) {
        return adminModelsService.check(body);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminModelEntryDto upsert(@AuthenticationPrincipal RagPrincipal principal, @Valid @RequestBody AdminModelUpsertRequest body) {
        return adminModelsService.upsert(body);
    }

    @PostMapping("/pull")
    public ResponseEntity<LabJobAcceptedDto> pull(
            @AuthenticationPrincipal RagPrincipal principal,
            @Valid @RequestBody PullOllamaModelRequest body) {
        UUID jobId = asyncTaskService.submitOllamaPull(principal.userId(), body.model().trim());
        String base = apiPathProperties.getProductBasePath() + "/lab/jobs/" + jobId;
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new LabJobAcceptedDto(jobId, "ACCEPTED", base, base + "/events"));
    }
}


package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.configuration.RagApiPathProperties;
import com.uniovi.rag.interfaces.rest.dto.LabJobAcceptedDto;
import com.uniovi.rag.interfaces.rest.dto.modelregistry.ModelRegistryCheckRequest;
import com.uniovi.rag.interfaces.rest.dto.modelregistry.ModelRegistryItemDto;
import com.uniovi.rag.interfaces.rest.dto.modelregistry.ModelRegistryPullRequest;
import com.uniovi.rag.interfaces.rest.dto.modelregistry.ModelRegistryResponseDto;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.application.service.async.AsyncTaskService;
import com.uniovi.rag.application.service.model.ModelRegistryService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Authenticated users: curated LLM/embedding registry, Ollama checks, and async pull for registry ids only.
 * Global DB allowlist changes remain on {@code /api/v5/admin/models} (ADMIN).
 */
@RestController
@RequestMapping("${rag.api.product-base-path}/model-registry")
public class ModelRegistryController {

    private final ModelRegistryService modelRegistryService;
    private final AsyncTaskService asyncTaskService;
    private final RagApiPathProperties apiPathProperties;

    public ModelRegistryController(
            ModelRegistryService modelRegistryService,
            AsyncTaskService asyncTaskService,
            RagApiPathProperties apiPathProperties) {
        this.modelRegistryService = modelRegistryService;
        this.asyncTaskService = asyncTaskService;
        this.apiPathProperties = apiPathProperties;
    }

    @GetMapping
    public ModelRegistryResponseDto list() {
        return modelRegistryService.snapshot();
    }

    @PostMapping("/check")
    public ModelRegistryItemDto check(@Valid @RequestBody ModelRegistryCheckRequest body) {
        return modelRegistryService.check(body.modelId(), body.probeEmbedding());
    }

    @PostMapping("/pull")
    public ResponseEntity<LabJobAcceptedDto> pull(
            @AuthenticationPrincipal RagPrincipal principal, @Valid @RequestBody ModelRegistryPullRequest body) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        modelRegistryService.assertPullAllowed(body.modelId());
        UUID jobId = asyncTaskService.submitOllamaPull(principal.userId(), body.modelId().trim());
        String base = apiPathProperties.getProductBasePath() + "/lab/jobs/" + jobId;
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new LabJobAcceptedDto(jobId, "ACCEPTED", base, base + "/events"));
    }
}

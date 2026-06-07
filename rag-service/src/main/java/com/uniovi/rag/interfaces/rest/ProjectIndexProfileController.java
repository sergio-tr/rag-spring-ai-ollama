package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.knowledge.ProjectIndexProfileApplicationService;
import com.uniovi.rag.interfaces.rest.dto.ProjectIndexProfileDto;
import com.uniovi.rag.interfaces.rest.dto.UpsertProjectIndexProfileRequest;
import com.uniovi.rag.security.RagPrincipal;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${rag.api.product-base-path}/projects/{projectId}/index-profile")
public class ProjectIndexProfileController {

    private final ProjectIndexProfileApplicationService applicationService;

    public ProjectIndexProfileController(
            ProjectIndexProfileApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping
    public ProjectIndexProfileDto get(@AuthenticationPrincipal RagPrincipal principal, @PathVariable UUID projectId) {
        return applicationService.get(principal.userId(), projectId);
    }

    @PutMapping
    public ProjectIndexProfileDto put(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable UUID projectId,
            @RequestBody UpsertProjectIndexProfileRequest body) {
        return applicationService.put(principal.userId(), projectId, body);
    }
}


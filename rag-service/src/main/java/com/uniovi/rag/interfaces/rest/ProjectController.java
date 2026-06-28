package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.interfaces.rest.dto.ActivateProjectResponseDto;
import com.uniovi.rag.interfaces.rest.dto.CreateProjectRequest;
import com.uniovi.rag.interfaces.rest.dto.PatchProjectRequest;
import com.uniovi.rag.interfaces.rest.dto.ProjectListResponseDto;
import com.uniovi.rag.interfaces.rest.dto.ProjectSummaryDto;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.application.service.project.ProjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("${rag.api.product-base-path}/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public ProjectListResponseDto list(
            @AuthenticationPrincipal RagPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size) {
        return projectService.list(principal.userId(), page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectSummaryDto create(
            @AuthenticationPrincipal RagPrincipal principal, @Valid @RequestBody CreateProjectRequest body) {
        return projectService.create(principal.userId(), body);
    }

    @GetMapping("/{projectId}")
    public ProjectSummaryDto get(
            @AuthenticationPrincipal RagPrincipal principal, @PathVariable UUID projectId) {
        return projectService.get(principal.userId(), projectId);
    }

    @PatchMapping("/{projectId}")
    public ProjectSummaryDto patch(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable UUID projectId,
            @Valid @RequestBody PatchProjectRequest body) {
        return projectService.patch(principal.userId(), projectId, body);
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal RagPrincipal principal, @PathVariable UUID projectId) {
        projectService.delete(principal.userId(), projectId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{projectId}/activate")
    public ActivateProjectResponseDto activate(
            @AuthenticationPrincipal RagPrincipal principal, @PathVariable UUID projectId) {
        return projectService.activate(principal.userId(), projectId);
    }
}

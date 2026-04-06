package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.knowledge.KnowledgeIngestionService;
import com.uniovi.rag.application.service.knowledge.ProjectKnowledgeApplicationService;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.interfaces.rest.dto.ProjectDocumentDto;
import com.uniovi.rag.interfaces.rest.dto.knowledge.KnowledgeSnapshotDetailResponse;
import com.uniovi.rag.interfaces.rest.dto.knowledge.KnowledgeSnapshotSummaryResponse;
import com.uniovi.rag.security.RagPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Canonical knowledge API (ingest, reindex, snapshots). Retrieval and RAG are not exposed here.
 */
@RestController
@RequestMapping("${rag.api.product-base-path}/projects/{projectId}/knowledge")
public class ProjectKnowledgeController {

    private final KnowledgeIngestionService knowledgeIngestionService;
    private final ProjectKnowledgeApplicationService projectKnowledgeApplicationService;

    public ProjectKnowledgeController(
            KnowledgeIngestionService knowledgeIngestionService,
            ProjectKnowledgeApplicationService projectKnowledgeApplicationService) {
        this.knowledgeIngestionService = knowledgeIngestionService;
        this.projectKnowledgeApplicationService = projectKnowledgeApplicationService;
    }

    @PostMapping(value = "/ingest", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProjectDocumentDto> ingest(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable UUID projectId,
            @RequestParam("corpusScope") CorpusScope corpusScope,
            @RequestParam(value = "conversationId", required = false) UUID conversationId,
            @RequestParam("file") MultipartFile file)
            throws IOException {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        ProjectDocumentDto dto;
        if (corpusScope == CorpusScope.CHAT_LOCAL) {
            if (conversationId == null) {
                return ResponseEntity.badRequest().build();
            }
            dto = knowledgeIngestionService.uploadConversationOverlay(
                    principal.userId(), projectId, conversationId, file);
        } else {
            if (conversationId != null) {
                return ResponseEntity.badRequest().build();
            }
            dto = knowledgeIngestionService.uploadProjectDocument(principal.userId(), projectId, file);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PostMapping("/reindex")
    public ResponseEntity<Void> reindex(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable UUID projectId,
            @RequestParam("corpusScope") CorpusScope corpusScope,
            @RequestParam(value = "conversationId", required = false) UUID conversationId) {
        projectKnowledgeApplicationService.triggerReindex(principal.userId(), projectId, corpusScope, conversationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/snapshots")
    public List<KnowledgeSnapshotSummaryResponse> listSnapshots(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable UUID projectId,
            @RequestParam("corpusScope") CorpusScope corpusScope,
            @RequestParam(value = "conversationId", required = false) UUID conversationId) {
        return projectKnowledgeApplicationService.listSnapshots(
                principal.userId(), projectId, corpusScope, conversationId);
    }

    @GetMapping("/snapshots/{snapshotId}")
    public KnowledgeSnapshotDetailResponse getSnapshot(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID snapshotId,
            @RequestParam("corpusScope") CorpusScope corpusScope,
            @RequestParam(value = "conversationId", required = false) UUID conversationId) {
        return projectKnowledgeApplicationService.getSnapshot(
                principal.userId(), projectId, snapshotId, corpusScope, conversationId);
    }
}

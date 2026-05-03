package com.uniovi.rag.interfaces.rest.me;

import com.uniovi.rag.application.service.me.MeDocumentQueryService;
import com.uniovi.rag.domain.ProjectDocumentStatus;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.interfaces.rest.dto.me.MeDocumentsPageResponse;
import com.uniovi.rag.security.RagPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Tag(name = "Me", description = "Global document inventory for data controls")
@RestController
@RequestMapping("${rag.api.product-base-path}/me/documents")
public class MeDocumentsController {

    private final MeDocumentQueryService meDocumentQueryService;

    public MeDocumentsController(MeDocumentQueryService meDocumentQueryService) {
        this.meDocumentQueryService = meDocumentQueryService;
    }

    @GetMapping
    public MeDocumentsPageResponse list(
            @AuthenticationPrincipal RagPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String corpusScope,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID conversationId,
            @RequestParam(required = false) String status) {
        CorpusScope cs = parseCorpusScope(corpusScope);
        ProjectDocumentStatus st = parseStatus(status);
        return meDocumentQueryService.list(
                principal.userId(), page, size, cs, projectId, conversationId, st);
    }

    private static CorpusScope parseCorpusScope(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return CorpusScope.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid corpusScope");
        }
    }

    private static ProjectDocumentStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ProjectDocumentStatus.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status");
        }
    }
}

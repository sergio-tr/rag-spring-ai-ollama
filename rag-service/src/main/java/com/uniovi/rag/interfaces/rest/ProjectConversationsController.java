package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.ConversationApplicationService;
import com.uniovi.rag.interfaces.rest.dto.ConversationDto;
import com.uniovi.rag.interfaces.rest.dto.CreateConversationRequest;
import com.uniovi.rag.security.RagPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("${rag.api.product-base-path}/projects/{projectId}/conversations")
public class ProjectConversationsController {

    private final ConversationApplicationService conversationApplicationService;

    public ProjectConversationsController(ConversationApplicationService conversationApplicationService) {
        this.conversationApplicationService = conversationApplicationService;
    }

    @GetMapping
    public List<ConversationDto> list(
            @AuthenticationPrincipal RagPrincipal principal, @PathVariable UUID projectId) {
        return conversationApplicationService.listConversations(principal.userId(), projectId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationDto create(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable UUID projectId,
            @RequestBody(required = false) CreateConversationRequest body) {
        return conversationApplicationService.createConversation(principal.userId(), projectId, body);
    }
}

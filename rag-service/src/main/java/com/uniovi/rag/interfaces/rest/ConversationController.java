package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.ChatMessageApplicationService;
import com.uniovi.rag.application.service.ConversationApplicationService;
import com.uniovi.rag.interfaces.rest.dto.ChatMessageAcceptedDto;
import com.uniovi.rag.interfaces.rest.dto.ConversationDraftDto;
import com.uniovi.rag.interfaces.rest.dto.ConversationDto;
import com.uniovi.rag.interfaces.rest.dto.LabJobAcceptedDto;
import com.uniovi.rag.interfaces.rest.dto.MessageDto;
import com.uniovi.rag.interfaces.rest.dto.PatchConversationRequest;
import com.uniovi.rag.interfaces.rest.dto.PatchUserMessageRequest;
import com.uniovi.rag.configuration.RagApiPathProperties;
import com.uniovi.rag.security.RagPrincipal;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("${rag.api.product-base-path}/conversations")
public class ConversationController {

    private final ConversationApplicationService conversationApplicationService;
    private final ChatMessageApplicationService chatMessageApplicationService;
    private final RagApiPathProperties apiPathProperties;

    public ConversationController(
            ConversationApplicationService conversationApplicationService,
            ChatMessageApplicationService chatMessageApplicationService,
            RagApiPathProperties apiPathProperties) {
        this.conversationApplicationService = conversationApplicationService;
        this.chatMessageApplicationService = chatMessageApplicationService;
        this.apiPathProperties = apiPathProperties;
    }

    @PatchMapping("/{conversationId}")
    public ConversationDto patchTitle(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable UUID conversationId,
            @RequestBody PatchConversationRequest body) {
        return conversationApplicationService.patchConversation(principal.userId(), conversationId, body);
    }

    @DeleteMapping("/{conversationId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal RagPrincipal principal, @PathVariable UUID conversationId) {
        conversationApplicationService.deleteConversation(principal.userId(), conversationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{conversationId}/messages")
    public List<MessageDto> messages(
            @AuthenticationPrincipal RagPrincipal principal, @PathVariable UUID conversationId) {
        return conversationApplicationService.listMessages(principal.userId(), conversationId);
    }

    @GetMapping("/{conversationId}/draft")
    public ConversationDraftDto getDraft(
            @AuthenticationPrincipal RagPrincipal principal, @PathVariable UUID conversationId) {
        return chatMessageApplicationService.getDraft(principal.userId(), conversationId);
    }

    @PutMapping("/{conversationId}/draft")
    public ConversationDraftDto putDraft(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable UUID conversationId,
            @RequestBody PatchUserMessageRequest body) {
        String c = body != null ? body.content() : "";
        return chatMessageApplicationService.putDraft(principal.userId(), conversationId, c);
    }

    @PatchMapping("/{conversationId}/messages/{messageId}")
    public ResponseEntity<Void> patchUserMessage(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable UUID conversationId,
            @PathVariable UUID messageId,
            @RequestBody PatchUserMessageRequest body) {
        if (body == null || body.content() == null || body.content().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content is required");
        }
        chatMessageApplicationService.editUserMessage(principal.userId(), conversationId, messageId, body.content());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{conversationId}/messages/{assistantMessageId}/retry")
    public ResponseEntity<LabJobAcceptedDto> retryAssistant(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable UUID conversationId,
            @PathVariable UUID assistantMessageId) {
        ChatMessageAcceptedDto accepted =
                chatMessageApplicationService.retryAssistantMessage(
                        principal.userId(), conversationId, assistantMessageId);
        String base = apiPathProperties.getProductBasePath() + "/lab/jobs/" + accepted.jobId();
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new LabJobAcceptedDto(accepted.jobId(), "ACCEPTED", base, base + "/events"));
    }
}

package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.ChatMessageApplicationService;
import com.uniovi.rag.configuration.RagApiPathProperties;
import com.uniovi.rag.interfaces.rest.dto.ChatMessageAcceptedDto;
import com.uniovi.rag.interfaces.rest.dto.LabJobAcceptedDto;
import com.uniovi.rag.interfaces.rest.dto.PostMessageRequest;
import com.uniovi.rag.security.RagPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Chat message submission: HTTP 202 + {@link com.uniovi.rag.domain.AsyncTaskType#CHAT_MESSAGE} job (poll/SSE under /lab/jobs).
 */
@RestController
@RequestMapping("${rag.api.product-base-path}/conversations")
public class MessageStreamController {

    private final ChatMessageApplicationService chatMessageApplicationService;
    private final RagApiPathProperties apiPathProperties;

    public MessageStreamController(
            ChatMessageApplicationService chatMessageApplicationService, RagApiPathProperties apiPathProperties) {
        this.chatMessageApplicationService = chatMessageApplicationService;
        this.apiPathProperties = apiPathProperties;
    }

    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<LabJobAcceptedDto> postMessage(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable UUID conversationId,
            @RequestBody PostMessageRequest body) {
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "body is required");
        }
        ChatMessageAcceptedDto accepted =
                chatMessageApplicationService.enqueueMessage(principal.userId(), conversationId, body);
        String base = apiPathProperties.getProductBasePath() + "/lab/jobs/" + accepted.jobId();
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new LabJobAcceptedDto(accepted.jobId(), "ACCEPTED", base, base + "/events"));
    }
}

package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.chat.ChatRuntimeStateService;
import com.uniovi.rag.interfaces.rest.dto.ChatRuntimeStateDto;
import com.uniovi.rag.security.RagPrincipal;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${rag.api.product-base-path}/conversations")
public class ChatRuntimeStateController {

    private final ChatRuntimeStateService chatRuntimeStateService;

    public ChatRuntimeStateController(ChatRuntimeStateService chatRuntimeStateService) {
        this.chatRuntimeStateService = chatRuntimeStateService;
    }

    @GetMapping("/{conversationId}/runtime-state")
    public ChatRuntimeStateDto runtimeState(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable UUID conversationId) {
        return chatRuntimeStateService.getRuntimeState(principal.userId(), conversationId);
    }
}


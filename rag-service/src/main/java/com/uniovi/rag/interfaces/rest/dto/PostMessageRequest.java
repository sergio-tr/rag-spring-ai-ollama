package com.uniovi.rag.interfaces.rest.dto;

import java.util.UUID;

public record PostMessageRequest(String content, String llmModel, UUID continueAfterUserMessageId) {
    public PostMessageRequest(String content, String llmModel, UUID continueAfterUserMessageId) {
        this.content = content != null ? content : "";
        this.llmModel = llmModel;
        this.continueAfterUserMessageId = continueAfterUserMessageId;
    }
}

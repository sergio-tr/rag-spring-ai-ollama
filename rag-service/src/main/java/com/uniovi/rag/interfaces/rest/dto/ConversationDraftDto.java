package com.uniovi.rag.interfaces.rest.dto;

import java.time.Instant;

public record ConversationDraftDto(String content, Instant updatedAt) {}

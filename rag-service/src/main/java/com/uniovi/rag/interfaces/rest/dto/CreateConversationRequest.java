package com.uniovi.rag.interfaces.rest.dto;

import java.util.List;

/**
 * Optional {@code documentFilter}: project document UUIDs to restrict retrieval for this chat (empty = all).
 */
public record CreateConversationRequest(String title, List<String> documentFilter) {}

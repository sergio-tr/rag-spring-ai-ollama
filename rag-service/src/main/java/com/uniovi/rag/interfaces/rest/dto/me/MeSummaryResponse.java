package com.uniovi.rag.interfaces.rest.dto.me;

public record MeSummaryResponse(
        long projectCount,
        long conversationCount,
        long documentCount,
        long estimatedStorageBytes) {}

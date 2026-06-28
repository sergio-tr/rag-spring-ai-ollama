package com.uniovi.rag.application.result.query;

/**
 * Draft response and the context used to produce it (for post-step and ranker).
 */
public record DraftAndContext(String draft, String context) {}

package com.uniovi.rag.application.model;

/**
 * Draft response and the context used to produce it (for post-step and ranker).
 */
public record DraftAndContext(String draft, String context) {}

package com.uniovi.rag.application.result.chat;

/** Effective retrieval values and provenance for chat runtime state assembly. */
public record EffectiveRetrievalParameters(
        int topK,
        double similarityThreshold,
        String topKSource,
        String similarityThresholdSource) {}

package com.uniovi.rag.domain.runtime.retrieval;

/** Metadata constraint filter outcome for advanced retrieval. */
public record MetadataFilterTelemetry(boolean applied, boolean fallback) {}

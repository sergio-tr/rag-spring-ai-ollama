package com.uniovi.rag.domain.product;

/**
 * Ollama presence / health for a curated registry row. UI may show an additional "pulling" state while a lab job runs.
 */
public enum ModelRegistryAvailabilityStatus {
    AVAILABLE,
    MISSING,
    ERROR
}

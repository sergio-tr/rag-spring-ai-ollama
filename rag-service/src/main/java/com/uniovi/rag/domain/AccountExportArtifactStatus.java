package com.uniovi.rag.domain;

/**
 * Lifecycle of a downloadable GDPR-style export ZIP.
 */
public enum AccountExportArtifactStatus {
    PENDING,
    READY,
    EXPIRED,
    DELETED
}

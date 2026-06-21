package com.uniovi.rag.domain.runtime.tool;

/** Strength of deterministic query-shape evidence for tool selection. */
public enum DeterministicEvidenceLevel {
    NONE,
    WEAK,
    STRONG,
    ORACLE
}

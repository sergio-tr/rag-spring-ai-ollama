package com.uniovi.rag.domain.runtime.retrieval;

public record RerankOutcome(String candidateId, double rerankScore, int finalRank) {}

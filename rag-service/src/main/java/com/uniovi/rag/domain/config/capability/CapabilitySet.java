package com.uniovi.rag.domain.config.capability;

import com.uniovi.rag.domain.runtime.RagConfig;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Serializable view of effective capabilities for diff, metrics, and reindex preview.
 */
public record CapabilitySet(
        Set<Capability> activeCapabilities,
        String embeddingModelId,
        String indexMode,
        String chunkingPolicy
) {

    public static CapabilitySet fromRagConfig(RagConfig c) {
        EnumSet<Capability> caps = EnumSet.noneOf(Capability.class);
        if (c.expansionEnabled()) {
            caps.add(Capability.EXPANSION);
        }
        if (c.nerEnabled()) {
            caps.add(Capability.NER);
        }
        if (c.toolsEnabled()) {
            caps.add(Capability.TOOLS);
        }
        if (c.metadataEnabled()) {
            caps.add(Capability.METADATA);
        }
        if (c.reasoningEnabled()) {
            caps.add(Capability.REASONING);
        }
        if (c.rankerEnabled()) {
            caps.add(Capability.RANKER);
        }
        if (c.postRetrievalEnabled()) {
            caps.add(Capability.POST_RETRIEVAL);
        }
        if (c.functionCallingEnabled()) {
            caps.add(Capability.FUNCTION_CALLING);
        }
        if (c.useRetrieval()) {
            caps.add(Capability.USE_RETRIEVAL);
        }
        if (c.useAdvisor()) {
            caps.add(Capability.USE_ADVISOR);
        }
        if (c.naiveFullCorpusInPromptEnabled()) {
            caps.add(Capability.NAIVE_FULL_CORPUS_PROMPT);
        }
        return new CapabilitySet(
                Set.copyOf(caps),
                c.embeddingModel(),
                "DEFAULT",
                "DEFAULT");
    }

    /**
     * True when any capability or embedding identity relevant to persisted index/embeddings differs.
     */
    public static boolean differsForReindex(CapabilitySet before, CapabilitySet after) {
        if (before == null || after == null) {
            return true;
        }
        return !Objects.equals(before.activeCapabilities(), after.activeCapabilities())
                || !Objects.equals(normalizeEmbedding(before.embeddingModelId()), normalizeEmbedding(after.embeddingModelId()))
                || !Objects.equals(before.indexMode(), after.indexMode())
                || !Objects.equals(before.chunkingPolicy(), after.chunkingPolicy());
    }

    private static String normalizeEmbedding(String id) {
        return id == null ? "" : id.trim();
    }
}

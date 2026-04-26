package com.uniovi.rag.infrastructure.persistence.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;
import com.uniovi.rag.domain.config.runtime.ConfigProvenance;
import com.uniovi.rag.domain.config.runtime.ResolvedConfigSnapshot;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.infrastructure.persistence.jpa.ResolvedConfigSnapshotEntity;
import com.uniovi.rag.infrastructure.persistence.mapper.ResolvedConfigSnapshotEntityMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds minimal {@link ResolvedRuntimeConfig} + {@link ResolvedConfigSnapshotEntity} graphs for JPA integration tests.
 */
public final class ResolvedConfigSnapshotTestFixtures {

    private ResolvedConfigSnapshotTestFixtures() {}

    public static RagConfig minimalRagConfig() {
        return new RagConfig(
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                5,
                0.1,
                "m",
                "e",
                "c",
                "simple",
                false,
                100,
                100,
                MaterializationStrategy.CHUNK_LEVEL);
    }

    public static ResolvedConfigSnapshotEntity newEntityForPersistence(
            ObjectMapper om, UUID snapshotId, UUID creatingUserId, UUID projectId, String configHash) {
        RagConfig core = minimalRagConfig();
        ResolvedRuntimeConfig resolved =
                new ResolvedRuntimeConfig(
                        core,
                        CapabilitySet.fromRagConfig(core),
                        CompatibilityResult.ok(),
                        ReindexImpact.none(),
                        SystemPromptLayers.empty(),
                        "eff",
                        new ConfigProvenance(null, null, null, List.of(), null, snapshotId),
                        core);
        ResolvedConfigSnapshot domainSnap =
                new ResolvedConfigSnapshot(
                        snapshotId,
                        Instant.parse("2026-01-01T00:00:00Z"),
                        resolved,
                        resolved.capabilitySet(),
                        resolved.compatibility(),
                        resolved.reindexImpact(),
                        resolved.effectiveSystemPrompt(),
                        resolved.provenance());
        ResolvedConfigSnapshotEntityMapper mapper = new ResolvedConfigSnapshotEntityMapper(om);
        ResolvedConfigSnapshotEntity e =
                mapper.toNewEntity(
                        resolved,
                        domainSnap,
                        creatingUserId,
                        configHash,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.ofNullable(projectId),
                        null);
        return e;
    }
}

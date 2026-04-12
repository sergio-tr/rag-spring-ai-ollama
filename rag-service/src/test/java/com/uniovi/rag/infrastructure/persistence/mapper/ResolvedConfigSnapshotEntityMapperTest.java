package com.uniovi.rag.infrastructure.persistence.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.service.knowledge.KnowledgeBuildProjectionMapper;
import com.uniovi.rag.domain.config.runtime.ResolvedConfigSnapshot;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.infrastructure.persistence.jpa.ResolvedConfigSnapshotEntity;
import com.uniovi.rag.infrastructure.persistence.support.ResolvedConfigSnapshotTestFixtures;
import com.uniovi.rag.interfaces.rest.dto.ResolvedConfigSnapshotResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ResolvedConfigSnapshotEntityMapperTest {

    @Test
    void toResponse_emptyMapsWhenJsonColumnsNullAndNormalizesStrings() {
        ObjectMapper om = new ObjectMapper();
        ResolvedConfigSnapshotEntityMapper mapper = new ResolvedConfigSnapshotEntityMapper(om);
        ResolvedConfigSnapshotEntity e = ResolvedConfigSnapshotEntity.newForInsert();
        UUID id = UUID.randomUUID();
        e.setId(id);
        Instant t = Instant.parse("2026-04-01T10:00:00Z");
        e.setCreatedAt(t);
        e.setPayloadJsonb(Map.of("x", 1));
        e.setCapabilitySetJsonb(null);
        e.setCompatibilityResultJsonb(Map.of());
        e.setReindexImpactJsonb(null);
        e.setSystemPromptLayersJsonb(null);
        e.setEffectiveSystemPrompt(null);
        e.setProvenanceJsonb(null);
        e.setConfigHash(null);
        e.setConversationId(UUID.randomUUID());

        ResolvedConfigSnapshotResponse r = mapper.toResponse(e);
        assertThat(r.id()).isEqualTo(id);
        assertThat(r.createdAt()).isEqualTo(t);
        assertThat(r.payload()).containsEntry("x", 1);
        assertThat(r.capabilitySet()).isEmpty();
        assertThat(r.compatibilityResult()).isEmpty();
        assertThat(r.reindexImpact()).isEmpty();
        assertThat(r.systemPromptLayers()).isEmpty();
        assertThat(r.effectiveSystemPrompt()).isEmpty();
        assertThat(r.provenance()).isEmpty();
        assertThat(r.configHash()).isEmpty();
        assertThat(r.conversationId()).isEqualTo(e.getConversationId());
    }

    @Test
    void toNewEntity_mergesKnowledgeProjectionIntoPayloadWhenNonEmpty() throws Exception {
        ObjectMapper om = new ObjectMapper();
        UUID snap = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        UUID project = UUID.randomUUID();
        var core = ResolvedConfigSnapshotTestFixtures.minimalRagConfig();
        ResolvedRuntimeConfig resolved =
                new ResolvedRuntimeConfig(
                        core,
                        com.uniovi.rag.domain.config.capability.CapabilitySet.fromRagConfig(core),
                        com.uniovi.rag.domain.config.validation.CompatibilityResult.ok(),
                        com.uniovi.rag.domain.config.indexing.ReindexImpact.none(),
                        com.uniovi.rag.domain.config.prompt.SystemPromptLayers.empty(),
                        "prompt",
                        new com.uniovi.rag.domain.config.runtime.ConfigProvenance(
                                null, null, null, java.util.List.of(), null, snap),
                        core);
        ResolvedConfigSnapshot domainSnap =
                new ResolvedConfigSnapshot(
                        snap,
                        Instant.parse("2026-01-01T00:00:00Z"),
                        resolved,
                        resolved.capabilitySet(),
                        resolved.compatibility(),
                        resolved.reindexImpact(),
                        resolved.effectiveSystemPrompt(),
                        resolved.provenance());
        ResolvedConfigSnapshotEntityMapper mapper = new ResolvedConfigSnapshotEntityMapper(om);
        Map<String, Object> nested = Map.of("built", true);
        ResolvedConfigSnapshotEntity e =
                mapper.toNewEntity(
                        resolved,
                        domainSnap,
                        user,
                        "h1",
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of("corr-1"),
                        Optional.of(project),
                        nested);
        assertThat(e.getPayloadJsonb()).containsKey(KnowledgeBuildProjectionMapper.PAYLOAD_KEY);
        @SuppressWarnings("unchecked")
        Map<String, Object> wrapped = (Map<String, Object>) e.getPayloadJsonb().get(KnowledgeBuildProjectionMapper.PAYLOAD_KEY);
        assertThat(wrapped).containsEntry("built", true);
        assertThat(e.getProvenanceJsonb())
                .containsEntry(ResolvedConfigSnapshotEntityMapper.PROVENANCE_CORRELATION_ID, "corr-1")
                .containsEntry(ResolvedConfigSnapshotEntityMapper.PROVENANCE_PROJECT_ID, project.toString());
    }

    @Test
    void toNewEntity_skipsBlankCorrelationIdInProvenance() {
        ObjectMapper om = new ObjectMapper();
        UUID snap = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        var core2 = ResolvedConfigSnapshotTestFixtures.minimalRagConfig();
        ResolvedRuntimeConfig resolved =
                new ResolvedRuntimeConfig(
                        core2,
                        com.uniovi.rag.domain.config.capability.CapabilitySet.fromRagConfig(core2),
                        com.uniovi.rag.domain.config.validation.CompatibilityResult.ok(),
                        com.uniovi.rag.domain.config.indexing.ReindexImpact.none(),
                        com.uniovi.rag.domain.config.prompt.SystemPromptLayers.empty(),
                        "p",
                        new com.uniovi.rag.domain.config.runtime.ConfigProvenance(
                                null, null, null, java.util.List.of(), null, snap),
                        core2);
        ResolvedConfigSnapshot domainSnap =
                new ResolvedConfigSnapshot(
                        snap,
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
                        user,
                        "h2",
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of("   "),
                        Optional.empty(),
                        null);
        assertThat(e.getProvenanceJsonb().keySet())
                .doesNotContain(ResolvedConfigSnapshotEntityMapper.PROVENANCE_CORRELATION_ID);
    }
}

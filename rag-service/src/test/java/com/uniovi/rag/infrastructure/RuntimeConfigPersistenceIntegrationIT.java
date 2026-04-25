package com.uniovi.rag.infrastructure;

import com.uniovi.Application;
import com.uniovi.rag.application.port.PresetProfileCompositionSources;
import com.uniovi.rag.domain.UserRole;
import com.uniovi.rag.domain.config.PresetProfilePayloadMerge;
import com.uniovi.rag.domain.config.runtime.ConfigProfileType;
import com.uniovi.rag.domain.config.runtime.ResolvedConfigSnapshot;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.infrastructure.config.JpaConfigurationSourceAdapter;
import com.uniovi.rag.infrastructure.config.ResolvedRuntimeConfigHasher;
import com.uniovi.rag.infrastructure.persistence.ConfigProfileRepository;
import com.uniovi.rag.infrastructure.persistence.RagPresetRepository;
import com.uniovi.rag.infrastructure.persistence.ResolvedConfigSnapshotRepository;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ConfigProfileEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.RagPresetEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.RagPresetProfileRefEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ResolvedConfigSnapshotEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.infrastructure.persistence.mapper.ResolvedConfigSnapshotEntityMapper;
import com.uniovi.rag.testsupport.TestAiStubConfiguration;
import com.uniovi.rag.testsupport.TestEnvironment;
import com.uniovi.rag.testsupport.TestcontainersDatasourceConfiguration;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;
import com.uniovi.rag.domain.config.runtime.ConfigProvenance;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = Application.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "rag.jwt.secret=test-secret-key-for-jwt-signing-must-be-long-enough-32",
            "management.otlp.tracing.endpoint=http://127.0.0.1:4318/v1/traces",
            "management.otlp.metrics.export.url=http://127.0.0.1:4318/v1/metrics"
        })
@Import({TestAiStubConfiguration.class, TestcontainersDatasourceConfiguration.class})
@ActiveProfiles("test")
@EnabledIf(
        value = "com.uniovi.rag.testsupport.TestEnvironment#isSpringBootPostgresAvailable",
        disabledReason = "Postgres/Testcontainers not available")
class RuntimeConfigPersistenceIntegrationIT {

    @Autowired
    private JpaConfigurationSourceAdapter configurationSourceAdapter;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConfigProfileRepository configProfileRepository;

    @Autowired
    private RagPresetRepository ragPresetRepository;

    @Autowired
    private ResolvedConfigSnapshotRepository resolvedConfigSnapshotRepository;

    @Autowired
    private ResolvedConfigSnapshotEntityMapper resolvedConfigSnapshotEntityMapper;

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void loadPresetProfileCompositionSources_returnsRawMapsWithoutMerge() {
        Instant now = Instant.now();
        UserEntity user =
                new UserEntity() {
                    {
                        setEmail("it-preset-" + UUID.randomUUID() + "@test.local");
                        setPasswordHash("x");
                        setRole(UserRole.USER);
                        setCreatedAt(now);
                    }
                };
        user = userRepository.save(user);

        ConfigProfileEntity p1 =
                ConfigProfileEntity.newDraft(
                        ConfigProfileType.EMBEDDING, 1, "p1", Map.of("layer", "first"), user, user, now);
        ConfigProfileEntity p2 =
                ConfigProfileEntity.newDraft(
                        ConfigProfileType.EMBEDDING, 1, "p2", Map.of("layer", "second"), user, user, now);
        p1 = configProfileRepository.save(p1);
        p2 = configProfileRepository.save(p2);

        RagPresetEntity preset =
                RagPresetEntity.newUserOwned(
                        user, "it-preset", null, List.of(), Map.of("presetOnly", true), now, now);
        preset = ragPresetRepository.save(preset);
        preset.getProfileRefs().add(RagPresetProfileRefEntity.link(preset, p1, 0, "a"));
        preset.getProfileRefs().add(RagPresetProfileRefEntity.link(preset, p2, 1, "b"));
        preset = ragPresetRepository.save(preset);

        Optional<PresetProfileCompositionSources> src =
                configurationSourceAdapter.loadPresetProfileCompositionSources(user.getId(), preset.getId());
        assertThat(src).isPresent();
        assertThat(src.get().presetValues()).containsEntry("presetOnly", true);
        assertThat(src.get().orderedProfilePayloads()).hasSize(2);
        assertThat(src.get().orderedProfilePayloads().getFirst()).containsEntry("layer", "first");
        assertThat(src.get().orderedProfilePayloads().get(1)).containsEntry("layer", "second");

        Map<String, Object> merged = PresetProfilePayloadMerge.merge(src.get().presetValues(), src.get().orderedProfilePayloads());
        assertThat(merged).containsEntry("layer", "second").containsEntry("presetOnly", true);
    }

    @Test
    @Transactional
    void resolvedConfigSnapshot_roundTripsMapperAndHash() {
        RagFeatureConfiguration fc = new RagFeatureConfiguration();
        RagConfig core = RagConfig.fromFeatureConfiguration(fc, 10, 0.7, "cm", "em", "c", "simple");
        ResolvedRuntimeConfig resolved =
                new ResolvedRuntimeConfig(
                        core,
                        CapabilitySet.fromRagConfig(core),
                        CompatibilityResult.ok(),
                        ReindexImpact.none(),
                        SystemPromptLayers.empty(),
                        "eff",
                        new ConfigProvenance(null, null, null, List.of(), null, null),
                        core);
        ResolvedConfigSnapshot snap =
                new ResolvedConfigSnapshot(
                        UUID.randomUUID(),
                        Instant.now(),
                        resolved,
                        resolved.capabilitySet(),
                        resolved.compatibility(),
                        resolved.reindexImpact(),
                        resolved.effectiveSystemPrompt(),
                        resolved.provenance());
        String hash = ResolvedRuntimeConfigHasher.sha256Hex(resolved);
        UUID owner = UUID.randomUUID();
        ResolvedConfigSnapshotEntity entity =
                resolvedConfigSnapshotEntityMapper.toNewEntity(
                        resolved, snap, owner, hash, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        entity = resolvedConfigSnapshotRepository.save(entity);
        resolvedConfigSnapshotRepository.flush();
        entityManager.clear();

        ResolvedConfigSnapshotEntity loaded =
                resolvedConfigSnapshotRepository.findById(entity.getId()).orElseThrow();
        assertThat(loaded.getConfigHash()).isEqualTo(hash);
        assertThat(loaded.getReindexImpactJsonb()).isNotEmpty();
        assertThat(loaded.getSystemPromptLayersJsonb()).isNotEmpty();
        assertThat(loaded.getEffectiveSystemPrompt()).isEqualTo("eff");
        assertThat(loaded.getProvenanceJsonb().get(ResolvedConfigSnapshotEntityMapper.PROVENANCE_CREATING_USER_ID))
                .isEqualTo(owner.toString());
        assertThat(loaded.getProvenanceJsonb().get(ResolvedConfigSnapshotEntityMapper.PROVENANCE_SCHEMA_VERSION))
                .isEqualTo(ResolvedConfigSnapshotEntityMapper.SNAPSHOT_SCHEMA_VERSION);

        assertThat(ResolvedRuntimeConfigHasher.sha256Hex(resolved)).isEqualTo(hash);
    }
}

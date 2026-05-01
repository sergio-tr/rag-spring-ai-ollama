package com.uniovi.rag.infrastructure.persistence.jpa;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.Application;
import com.uniovi.rag.domain.config.runtime.ConfigProfileType;
import com.uniovi.rag.infrastructure.persistence.ConfigProfileRepository;
import com.uniovi.rag.infrastructure.persistence.ProjectRepository;
import com.uniovi.rag.infrastructure.persistence.RagConfigurationRepository;
import com.uniovi.rag.infrastructure.persistence.RagPresetProfileRefRepository;
import com.uniovi.rag.infrastructure.persistence.RagPresetRepository;
import com.uniovi.rag.infrastructure.persistence.ResolvedConfigSnapshotRepository;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.infrastructure.persistence.support.ResolvedConfigSnapshotTestFixtures;
import com.uniovi.rag.testsupport.TestAiStubConfiguration;
import com.uniovi.rag.testsupport.TestEnvironment;
import com.uniovi.rag.testsupport.TestcontainersDatasourceConfiguration;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

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
class RagConfigurationProfilePresetSnapshotJpaIT {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private RagConfigurationRepository ragConfigurationRepository;

    @Autowired
    private ConfigProfileRepository configProfileRepository;

    @Autowired
    private ResolvedConfigSnapshotRepository resolvedConfigSnapshotRepository;

    @Autowired
    private RagPresetRepository ragPresetRepository;

    @Autowired
    private RagPresetProfileRefRepository ragPresetProfileRefRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void persistRagConfigurationsProfileResolvedSnapshotAndPresetRef() {
        Instant now = Instant.parse("2026-05-15T10:00:00Z");
        UserEntity user =
                userRepository.save(
                        UserEntityFactory.newRegisteredUser(
                                "jpa-cfg-" + UUID.randomUUID() + "@test.local", "Cfg User", "ph"));

        ProjectEntity project = new ProjectEntity();
        project.setOwner(user);
        project.setName("cfg-project");
        project.setCreatedAt(now);
        project.setUpdatedAt(now);
        project = projectRepository.save(project);

        Map<String, Object> values = Map.of("useAdvisor", true);
        RagConfigurationEntity userDefault =
                RagConfigurationEntityFactory.newUserDefault(user, values, now);
        RagConfigurationEntity projectCfg =
                RagConfigurationEntityFactory.newProjectScoped(user, project, values, now);
        ragConfigurationRepository.save(userDefault);
        ragConfigurationRepository.save(projectCfg);

        ConfigProfileEntity profile =
                ConfigProfileEntity.newDraft(
                        ConfigProfileType.INDEX,
                        1,
                        "it-profile",
                        Map.of("k", 1),
                        user,
                        user,
                        now);
        profile = configProfileRepository.save(profile);

        UUID snapId = UUID.randomUUID();
        ResolvedConfigSnapshotEntity snap =
                ResolvedConfigSnapshotTestFixtures.newEntityForPersistence(
                        objectMapper, snapId, user.getId(), project.getId(), "snap-hash");
        snap = resolvedConfigSnapshotRepository.save(snap);

        RagPresetEntity preset =
                RagPresetEntity.newUserOwned(
                        user,
                        "it-preset",
                        "desc",
                        List.of("t1"),
                        Map.of("rag", true),
                        now,
                        now);
        preset = ragPresetRepository.save(preset);

        RagPresetProfileRefEntity link = RagPresetProfileRefEntity.link(preset, profile, 0, "INDEX");
        link = ragPresetProfileRefRepository.save(link);

        ragPresetProfileRefRepository.flush();
        entityManager.clear();

        RagPresetProfileRefEntity loaded =
                ragPresetProfileRefRepository.findById(link.getId()).orElseThrow();
        assertThat(loaded.getOrdinal()).isZero();
        assertThat(loaded.getRole()).isEqualTo("INDEX");

        ResolvedConfigSnapshotEntity s2 =
                resolvedConfigSnapshotRepository.findById(snap.getId()).orElseThrow();
        assertThat(s2.getConfigHash()).isEqualTo("snap-hash");
        assertThat(s2.getPayloadJsonb()).isNotEmpty();

        ConfigProfileEntity p2 = configProfileRepository.findById(profile.getId()).orElseThrow();
        assertThat(p2.getProfileType()).isEqualTo(ConfigProfileType.INDEX);
        assertThat(p2.getPayload()).containsEntry("k", 1);
    }
}

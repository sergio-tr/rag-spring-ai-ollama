package com.uniovi.rag.infrastructure.persistence.jpa;

import com.uniovi.Application;
import com.uniovi.rag.domain.AccountExportArtifactStatus;
import com.uniovi.rag.domain.AllowedModelType;
import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.domain.ClassifierModelStatus;
import com.uniovi.rag.domain.EvaluationDatasetType;
import com.uniovi.rag.domain.evaluation.EvaluationDatasetScope;
import com.uniovi.rag.infrastructure.persistence.AccountExportArtifactRepository;
import com.uniovi.rag.infrastructure.persistence.AllowedModelRepository;
import com.uniovi.rag.infrastructure.persistence.AsyncTaskRepository;
import com.uniovi.rag.infrastructure.persistence.ClassifierModelRepository;
import com.uniovi.rag.infrastructure.persistence.DefaultSystemConfigurationRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationDatasetRepository;
import com.uniovi.rag.infrastructure.persistence.PromptTemplateRepository;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.testsupport.TestAiStubConfiguration;
import com.uniovi.rag.testsupport.TestcontainersDatasourceConfiguration;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
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
class MiscLabPersistenceJpaIT {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DefaultSystemConfigurationRepository defaultSystemConfigurationRepository;

    @Autowired
    private AllowedModelRepository allowedModelRepository;

    @Autowired
    private PromptTemplateRepository promptTemplateRepository;

    @Autowired
    private AsyncTaskRepository asyncTaskRepository;

    @Autowired
    private EvaluationDatasetRepository evaluationDatasetRepository;

    @Autowired
    private ClassifierModelRepository classifierModelRepository;

    @Autowired
    private AccountExportArtifactRepository accountExportArtifactRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void persistDefaultConfigAllowedModelPromptAsyncClassifierAndExportArtifact() {
        Instant now = Instant.parse("2026-05-25T11:00:00Z");
        UserEntity user =
                userRepository.save(
                        UserEntityFactory.newRegisteredUser(
                                "jpa-misc-" + UUID.randomUUID() + "@test.local", "Misc User", "ph"));

        DefaultSystemConfigurationEntity def = new DefaultSystemConfigurationEntity();
        def.setValues(Map.of("flag", true));
        def.setUpdatedAt(now);
        def = defaultSystemConfigurationRepository.save(def);

        AllowedModelEntity allowed =
                allowedModelRepository.save(
                        AllowedModelEntity.newRow("llama-x", AllowedModelType.LLM, true, now));

        PromptTemplateEntity tmpl = new PromptTemplateEntity();
        tmpl.setName("it-tmpl");
        tmpl.setVersion(1);
        tmpl.setBody(Map.of("text", "hello"));
        tmpl.setCreatedAt(now);
        tmpl.setUpdatedAt(now);
        tmpl = promptTemplateRepository.save(tmpl);

        AsyncTaskEntity task =
                asyncTaskRepository.save(
                        AsyncTaskEntity.queued(
                                user, AsyncTaskType.ACCOUNT_EXPORT, Map.of("k", "v"), now));

        EvaluationDatasetEntity ds = new EvaluationDatasetEntity();
        ds.setOwner(user);
        ds.setName("cls-ds");
        ds.setType(EvaluationDatasetType.CLASSIFIER);
        ds.setUploadedAt(now);
        ds.setDatasetScope(EvaluationDatasetScope.USER_DATASET.name());
        ds = evaluationDatasetRepository.save(ds);

        ClassifierModelEntity model = new ClassifierModelEntity();
        model.setOwner(user);
        model.setName("clf-1");
        model.setDataset(ds);
        model.setHyperparams(Map.of("lr", 0.1));
        model.setActive(false);
        model.setPassesGate(true);
        model.setStatus(ClassifierModelStatus.TRAINING);
        model = classifierModelRepository.save(model);

        UUID exportId = UUID.randomUUID();
        AccountExportArtifactEntity export = AccountExportArtifactEntity.newArtifact();
        export.setId(exportId);
        export.setUser(user);
        export.setAsyncTask(task);
        export.setStorageUri("s3://bucket/key.zip");
        export.setSha256("0".repeat(64));
        export.setByteSize(1024L);
        export.setStatus(AccountExportArtifactStatus.READY);
        export.setCreatedAt(now);
        export.setExpiresAt(now.plusSeconds(3600));
        accountExportArtifactRepository.save(export);

        asyncTaskRepository.flush();
        entityManager.clear();

        DefaultSystemConfigurationEntity d2 =
                defaultSystemConfigurationRepository.findById(def.getId()).orElseThrow();
        assertThat(d2.getValues()).containsEntry("flag", true);

        PromptTemplateEntity t2 = promptTemplateRepository.findById(tmpl.getId()).orElseThrow();
        assertThat(t2.getName()).isEqualTo("it-tmpl");
        assertThat(t2.getBody()).containsEntry("text", "hello");

        AsyncTaskEntity tsk = asyncTaskRepository.findById(task.getId()).orElseThrow();
        assertThat(tsk.getTaskType()).isEqualTo(AsyncTaskType.ACCOUNT_EXPORT);

        ClassifierModelEntity m2 = classifierModelRepository.findById(model.getId()).orElseThrow();
        assertThat(m2.getStatus()).isEqualTo(ClassifierModelStatus.TRAINING);
        assertThat(m2.getHyperparams()).containsEntry("lr", 0.1);

        AccountExportArtifactEntity ex2 =
                accountExportArtifactRepository.findById(exportId).orElseThrow();
        assertThat(ex2.getStatus()).isEqualTo(AccountExportArtifactStatus.READY);
        assertThat(ex2.getByteSize()).isEqualTo(1024L);
    }
}

package com.uniovi.rag.infrastructure.persistence.jpa;

import com.uniovi.Application;
import com.uniovi.rag.domain.EvaluationDatasetType;
import com.uniovi.rag.domain.EvaluationRunStatus;
import com.uniovi.rag.domain.EvaluationRunType;
import com.uniovi.rag.domain.evaluation.EvaluationDatasetScope;
import com.uniovi.rag.infrastructure.persistence.EvaluationDatasetRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationResultRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
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
import java.util.List;
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
class EvaluationStackJpaIT {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EvaluationDatasetRepository evaluationDatasetRepository;

    @Autowired
    private EvaluationRunRepository evaluationRunRepository;

    @Autowired
    private EvaluationResultRepository evaluationResultRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void persistDatasetRunAndResult() {
        Instant now = Instant.parse("2026-05-10T08:00:00Z");
        UserEntity user =
                userRepository.save(
                        UserEntityFactory.newRegisteredUser(
                                "jpa-eval-" + UUID.randomUUID() + "@test.local", "Eval User", "ph"));

        EvaluationDatasetEntity dataset = new EvaluationDatasetEntity();
        dataset.setOwner(user);
        dataset.setName("ds-it");
        dataset.setType(EvaluationDatasetType.RAG);
        dataset.setUploadedAt(now);
        dataset.setDatasetScope(EvaluationDatasetScope.USER_DATASET.name());
        dataset = evaluationDatasetRepository.save(dataset);

        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setUser(user);
        run.setDataset(dataset);
        run.setType(EvaluationRunType.RAG_FULL);
        run.setConfigIds(List.of("cfg-a"));
        run.setStatus(EvaluationRunStatus.RUNNING);
        run.setProgress(2);
        run.setCreatedAt(now);
        run.setAggregatesJson(Map.of("n", 1));
        run = evaluationRunRepository.save(run);

        EvaluationResultEntity result = new EvaluationResultEntity();
        result.setRun(run);
        result.setQuestionText("What is RAG?");
        result.setExpectedAnswer("ctx");
        result.setActualAnswer("ctx+gen");
        result.setCorrectness(1);
        result.setLatencyMs(120L);
        result.setEvaluatedAt(now);
        result.setMetricsPayload(Map.of("score", 0.9));
        result = evaluationResultRepository.save(result);

        evaluationResultRepository.flush();
        entityManager.clear();

        EvaluationRunEntity runLoaded = evaluationRunRepository.findById(run.getId()).orElseThrow();
        assertThat(runLoaded.getStatus()).isEqualTo(EvaluationRunStatus.RUNNING);
        assertThat(runLoaded.getConfigIds()).containsExactly("cfg-a");
        assertThat(runLoaded.getAggregatesJson()).containsEntry("n", 1);

        EvaluationResultEntity resLoaded =
                evaluationResultRepository.findById(result.getId()).orElseThrow();
        assertThat(resLoaded.getQuestionText()).isEqualTo("What is RAG?");
        assertThat(resLoaded.getLatencyMs()).isEqualTo(120L);
    }
}

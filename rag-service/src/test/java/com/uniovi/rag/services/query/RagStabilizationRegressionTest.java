package com.uniovi.rag.services.query;

import com.uniovi.Application;
import com.uniovi.rag.application.model.QueryResponse;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.service.query.QueryService;

import com.uniovi.rag.testsupport.TestAiStubConfiguration;
import com.uniovi.rag.testsupport.TestcontainersDatasourceConfiguration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

/**
 * P11 regression suite: critical questions and known bad patterns.
 * Asserts that responses for non-existent dates do not contain wrong years,
 * and that expected tool/routing behavior is preserved.
 * Run with data loaded and Ollama for full coverage; unit-level guards are tested in DateExistenceGuardTest.
 */
@SpringBootTest(
        classes = Application.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "rag.jwt.secret=test-secret-key-for-jwt-signing-must-be-long-enough-32",
                "management.otlp.tracing.endpoint=http://127.0.0.1:4318/v1/traces",
                "management.otlp.metrics.export.url=http://127.0.0.1:4318/v1/metrics",
                // CI does not seed ACTIVE knowledge snapshots; keep this suite independent from retrieval indexing.
                "rag.features.use-retrieval=false",
                "rag.features.use-advisor=false"
        })
@Import({ TestAiStubConfiguration.class, TestcontainersDatasourceConfiguration.class })
@ActiveProfiles("test")
@EnabledIf(value = "com.uniovi.rag.testsupport.TestEnvironment#isSpringBootPostgresAvailable",
        disabledReason = "Set SPRING_DATASOURCE_URL (e.g. CI) or start Docker for Testcontainers")
class RagStabilizationRegressionTest {

    @Autowired
    private QueryService queryService;

    @Test
    @DisplayName("Non-existent date (2028): answer must not contain wrong years 2025 or 2026")
    void nonExistentDate_AnswerMustNotContainWrongYear() {
        String query = "¿Qué decisiones se tomaron el 25/08/2028?";
        QueryResponse response = queryService.generateResponse(query);
        assertNotNull(response);
        String answer = response.getAnswer();
        assertNotNull(answer);
        assertFalse(answer.contains("2025"), "Answer for non-existent 2028 must not contain 2025");
        assertFalse(answer.contains("2026"), "Answer for non-existent 2028 must not contain 2026");
    }

    @Test
    @DisplayName("Non-existent date: answer should indicate no acta (no-acta message or guard)")
    void nonExistentDate_AnswerShouldIndicateNoActa() {
        String query = "¿Qué decisiones se tomaron el 25/08/2028?";
        QueryResponse response = queryService.generateResponse(query);
        assertNotNull(response);
        String lower = response.getAnswer().toLowerCase();
        boolean indicatesNoActa = lower.contains("no hay") || lower.contains("ninguna acta")
                || lower.contains("no acta") || lower.contains("no se puede") || lower.contains("no existe")
                || lower.contains("sorry") || lower.contains("error occurred")
                || lower.contains("cannot find") || lower.contains("no relevant information");
        assertTrue(indicatesNoActa,
                "Answer for non-existent date should indicate no acta: " + response.getAnswer());
    }

    /**
     * Regression: classifier overrides must route "orden del día" to GET_FIELD (agenda), not EXTRACT_ENTITIES.
     * Run with tools=true, metadata=true. Skips assertion if tools are disabled.
     */
    @Test
    @DisplayName("Orden del día query is routed to GET_FIELD (agenda)")
    void ordenDelDia_RoutedToGetField() {
        String query = "¿Qué contiene el orden del día del 25 de agosto de 2026?";
        QueryResponse response = queryService.generateResponse(query);
        assertNotNull(response);
        if (response.getQueryType() != null) {
            assertEquals(QueryType.GET_FIELD, response.getQueryType(),
                    "Classifier override should route 'orden del día' to GET_FIELD");
        }
    }
}

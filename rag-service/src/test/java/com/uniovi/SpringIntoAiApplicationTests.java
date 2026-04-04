package com.uniovi;

import com.uniovi.rag.testsupport.TestAiStubConfiguration;
import com.uniovi.rag.testsupport.TestcontainersDatasourceConfiguration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
@SpringBootTest(
        classes = Application.class,
        properties = {
                "rag.jwt.secret=test-secret-key-for-jwt-signing-must-be-long-enough-32",
                "management.otlp.tracing.endpoint=http://127.0.0.1:4318/v1/traces",
                "management.otlp.metrics.export.url=http://127.0.0.1:4318/v1/metrics"
        })
@Import({ TestAiStubConfiguration.class, TestcontainersDatasourceConfiguration.class })
@ActiveProfiles("test")
@EnabledIf(value = "com.uniovi.rag.testsupport.TestEnvironment#isSpringBootPostgresAvailable",
        disabledReason = "Set SPRING_DATASOURCE_URL (e.g. CI) or start Docker for Testcontainers")
class SpringIntoAiApplicationTests {

	@Test
	void contextLoads() {
	}
}

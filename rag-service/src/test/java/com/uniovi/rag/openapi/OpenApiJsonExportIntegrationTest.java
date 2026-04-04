package com.uniovi.rag.openapi;

import com.uniovi.Application;
import com.uniovi.rag.testsupport.SafeTestSecretsApplicationContextInitializer;
import com.uniovi.rag.testsupport.TestAiStubConfiguration;
import com.uniovi.rag.testsupport.TestcontainersDatasourceConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Fetches springdoc /v3/api-docs from a running app and writes target/openapi.json for annexes or codegen
 * (runs only when Postgres is available, same as other full-context tests).
 */
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = SafeTestSecretsApplicationContextInitializer.class)
@Import({TestAiStubConfiguration.class, TestcontainersDatasourceConfiguration.class})
@ActiveProfiles("test")
@EnabledIf(
        value = "com.uniovi.rag.testsupport.TestEnvironment#isSpringBootPostgresAvailable",
        disabledReason = "Set SPRING_DATASOURCE_URL (e.g. CI) or start Docker for Testcontainers")
class OpenApiJsonExportIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void writesOpenApiDocumentToTarget() throws Exception {
        ResponseEntity<String> res =
                restTemplate.getForEntity("http://127.0.0.1:" + port + "/v3/api-docs", String.class);
        assertEquals(HttpStatus.OK, res.getStatusCode());
        String body = res.getBody();
        assertNotNull(body);
        assertTrue(body.contains("\"openapi\""), "response should be OpenAPI JSON");
        Path out = Path.of("target/openapi.json");
        Files.createDirectories(out.getParent());
        Files.writeString(out, body, StandardCharsets.UTF_8);
    }
}

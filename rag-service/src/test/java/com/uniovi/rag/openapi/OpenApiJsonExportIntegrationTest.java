package com.uniovi.rag.openapi;

import com.uniovi.Application;
import com.uniovi.rag.testsupport.TestAiStubConfiguration;
import com.uniovi.rag.testsupport.TestcontainersDatasourceConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Fetches springdoc /v3/api-docs from a running app and writes target/openapi.json for annexes or codegen
 * (runs only when Postgres is available, same as other full-context tests).
 */
@SpringBootTest(
        classes = Application.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "rag.jwt.secret=test-secret-key-for-jwt-signing-must-be-long-enough-32",
                "management.otlp.tracing.endpoint=http://127.0.0.1:4318/v1/traces",
                "management.otlp.metrics.export.url=http://127.0.0.1:4318/v1/metrics",
                // CI may set SPRINGDOC_API_DOCS_ENABLED=false via environment; force it on for this export test.
                "springdoc.api-docs.enabled=true"
        })
@Import({ TestAiStubConfiguration.class, TestcontainersDatasourceConfiguration.class })
@ActiveProfiles("test")
@EnabledIf(
        value = "com.uniovi.rag.testsupport.TestEnvironment#isSpringBootPostgresAvailable",
        disabledReason = "Set SPRING_DATASOURCE_URL (e.g. CI) or start Docker for Testcontainers")
class OpenApiJsonExportIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void writesOpenApiDocumentToTarget() throws Exception {
        ResponseEntity<String> res =
                restTemplate.getForEntity("http://127.0.0.1:" + port + "/v3/api-docs", String.class);
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertTrue(
                res.getHeaders().getContentType() == null
                        || MediaType.APPLICATION_JSON.isCompatibleWith(res.getHeaders().getContentType()),
                "response content-type should be JSON");
        String body = res.getBody();
        assertNotNull(body);
        Path out = Path.of("target/openapi.json");
        Files.createDirectories(out.getParent());
        JsonNode json = objectMapper.readTree(body);
        String jsonBodyForFile = body;
        // Some environments return OpenAPI as a base64-encoded JSON string (TextNode) rather than a JSON object.
        if (json.isTextual()) {
            byte[] decoded = Base64.getDecoder().decode(json.asText());
            jsonBodyForFile = new String(decoded, StandardCharsets.UTF_8);
            json = objectMapper.readTree(jsonBodyForFile);
        }
        // Always write the decoded body to disk to make CI/local failures diagnosable.
        Files.writeString(out, jsonBodyForFile, StandardCharsets.UTF_8);
        assertTrue(json.has("openapi"), "response should be OpenAPI JSON (see target/openapi.json)");

        // Swagger/OpenAPI security must declare Bearer JWT and mark protected endpoints.
        JsonNode schemes = json.path("components").path("securitySchemes");
        assertTrue(schemes.has("bearerAuth"), "OpenAPI must declare components.securitySchemes.bearerAuth");
        assertTrue(
                schemes.path("bearerAuth").path("type").asText("").equalsIgnoreCase("http"),
                "bearerAuth.type must be http");
        assertTrue(
                schemes.path("bearerAuth").path("scheme").asText("").equalsIgnoreCase("bearer"),
                "bearerAuth.scheme must be bearer");

        // Protected example: /api/v5/me/summary GET should require bearerAuth.
        JsonNode meSummaryGet = json.path("paths").path("/api/v5/me/summary").path("get");
        assertTrue(meSummaryGet.isObject(), "OpenAPI must include /api/v5/me/summary GET path");
        JsonNode sec = meSummaryGet.path("security");
        assertTrue(sec.isArray() && sec.size() > 0, "Protected operations must include security requirements");
        boolean hasBearer = false;
        for (JsonNode entry : sec) {
            if (entry.has("bearerAuth")) {
                hasBearer = true;
                break;
            }
        }
        assertTrue(hasBearer, "Protected operations must require bearerAuth");

        // Public example: /api/auth/login should not require bearerAuth.
        JsonNode loginPost = json.path("paths").path("/api/auth/login").path("post");
        assertTrue(loginPost.isObject(), "OpenAPI must include /api/auth/login POST path");
        assertTrue(
                !loginPost.has("security") || loginPost.path("security").isMissingNode() || loginPost.path("security").size() == 0,
                "Public auth endpoints must not require bearerAuth");
    }
}

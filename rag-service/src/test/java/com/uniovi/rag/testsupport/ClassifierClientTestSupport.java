package com.uniovi.rag.testsupport;

import com.uniovi.rag.infrastructure.classifier.ClassifierServiceClient;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

/**
 * Reusable harness for {@link ClassifierServiceClient} tests using Spring
 * {@link MockRestServiceServer} (in-process; no real HTTP to classifier-service).
 * <p>
 * Prefer this over a real {@code localhost:8000} connection. Use
 * {@link #defaultBaseUrl()} in {@code server.expect(requestTo(...))} so expectations
 * match the client base URL.
 */
public final class ClassifierClientTestSupport {

    /**
     * Canonical base URL for mock expectations. Must match the URL passed to
     * {@link ClassifierServiceClient} when using {@link #newDefaultFixture()}.
     */
    public static final String DEFAULT_MOCK_CLASSIFIER_BASE_URL = "http://localhost:8000";

    private ClassifierClientTestSupport() {
    }

    /**
     * Same as {@link #DEFAULT_MOCK_CLASSIFIER_BASE_URL} for readability in tests.
     */
    public static String defaultBaseUrl() {
        return DEFAULT_MOCK_CLASSIFIER_BASE_URL;
    }

    /**
     * Binds a {@link MockRestServiceServer} to a new {@link RestTemplate} and wires a
     * {@link ClassifierServiceClient} that uses that template (so all POSTs are intercepted).
     */
    public static ClassifierMockRestFixture newFixture(String baseUrl, String modelId, int timeoutMs) {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        ClassifierServiceClient client = new ClassifierServiceClient(baseUrl, modelId, timeoutMs, restTemplate);
        return new ClassifierMockRestFixture(server, restTemplate, client);
    }

    /**
     * Fixture with {@link #DEFAULT_MOCK_CLASSIFIER_BASE_URL}, model {@code default}, timeout {@code 5000} ms.
     */
    public static ClassifierMockRestFixture newDefaultFixture() {
        return newFixture(DEFAULT_MOCK_CLASSIFIER_BASE_URL, "default", 5000);
    }

    /**
     * Holds the mock server, shared {@link RestTemplate}, and client under test.
     *
     * @param server        mock HTTP layer (verify in {@code @AfterEach} or end of test)
     * @param restTemplate  same instance the client uses
     * @param client        classifier client bound to {@code restTemplate}
     */
    public record ClassifierMockRestFixture(
            MockRestServiceServer server,
            RestTemplate restTemplate,
            ClassifierServiceClient client) {
    }
}

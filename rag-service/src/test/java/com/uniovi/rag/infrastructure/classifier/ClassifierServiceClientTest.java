package com.uniovi.rag.infrastructure.classifier;

import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.RagExecutionContext;
import com.uniovi.rag.domain.runtime.RagExecutionContextHolder;
import com.uniovi.rag.testsupport.ClassifierClientTestSupport;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ClassifierServiceClientTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private ClassifierServiceClient classifier;

    @BeforeEach
    void setUp() {
        var fixture = ClassifierClientTestSupport.newDefaultFixture();
        restTemplate = fixture.restTemplate();
        server = fixture.server();
        classifier = fixture.client();
    }

    @Test
    void constructor_stripsTrailingSlashesFromBaseUrl() throws Exception {
        ClassifierServiceClient c =
                new ClassifierServiceClient("http://example.test///", "default", 1000, restTemplate);
        var f = ClassifierServiceClient.class.getDeclaredField("baseUrl");
        f.setAccessible(true);
        assertEquals("http://example.test", f.get(c));
    }

    @Test
    void classify_returnsQueryType_whenServiceReturns200WithValidQueryType() {
        String base = ClassifierClientTestSupport.defaultBaseUrl();
        server.expect(requestTo(base + "/classify"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"query\":\"How many documents?\",\"modelId\":\"default\"}"))
                .andRespond(withSuccess("{\"queryType\": \"COUNT_DOCUMENTS\"}", MediaType.APPLICATION_JSON));

        QueryType result = classifier.classify("How many documents?");

        server.verify();
        assertEquals(QueryType.COUNT_DOCUMENTS, result);
    }

    @Test
    void classifyUsesClassifierModelIdFromExecutionContext() {
        String base = ClassifierClientTestSupport.defaultBaseUrl();
        RagConfig cfg = RagConfig.fromFeatureConfiguration(
                new RagFeatureConfiguration(), 10, 0.7, "llm", "emb", "project-classifier", "SIMPLE");
        RagExecutionContextHolder.set(
                new RagExecutionContext("conv", "user", "project", cfg, List.of(RagExecutionContext.ALL_DOCUMENTS), "t"));
        try {
            server.expect(requestTo(base + "/classify"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(content().json("{\"query\":\"How many documents?\",\"modelId\":\"project-classifier\"}"))
                    .andRespond(withSuccess("{\"queryType\": \"COUNT_DOCUMENTS\"}", MediaType.APPLICATION_JSON));

            QueryType result = classifier.classify("How many documents?");

            server.verify();
            assertEquals(QueryType.COUNT_DOCUMENTS, result);
        } finally {
            RagExecutionContextHolder.clear();
        }
    }

    @Test
    void classifyWithText_returnsString_whenServiceReturns200() {
        String base = ClassifierClientTestSupport.defaultBaseUrl();
        server.expect(requestTo(base + "/classify"))
                .andRespond(withSuccess("{\"queryType\": \"FIND_PARAGRAPH\"}", MediaType.APPLICATION_JSON));

        String result = classifier.classifyWithText("Find the paragraph about X");

        server.verify();
        assertEquals("FIND_PARAGRAPH", result);
    }

    @Test
    void classify_returnsNull_whenServiceReturns5xx() {
        String base = ClassifierClientTestSupport.defaultBaseUrl();
        server.expect(requestTo(base + "/classify"))
                .andRespond(withServerError());

        QueryType result = classifier.classify("any query");

        server.verify();
        assertNull(result);
    }

    @Test
    void classify_returnsNull_whenServiceReturnsInvalidQueryType() {
        String base = ClassifierClientTestSupport.defaultBaseUrl();
        server.expect(requestTo(base + "/classify"))
                .andRespond(withSuccess("{\"queryType\": \"NOT_A_REAL_ENUM\"}", MediaType.APPLICATION_JSON));

        QueryType result = classifier.classify("any query");

        server.verify();
        assertNull(result);
    }

    @Test
    void classify_returnsNull_whenServiceReturnsNon2xx() {
        String base = ClassifierClientTestSupport.defaultBaseUrl();
        server.expect(requestTo(base + "/classify"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        QueryType result = classifier.classify("any query");

        server.verify();
        assertNull(result);
    }

    @Test
    void classifyWithText_returnsNull_whenRestTemplateThrowsRestClientException_timeout() {
        RestTemplate throwingRestTemplate = mock(RestTemplate.class);
        when(throwingRestTemplate.exchange(
                Mockito.anyString(),
                Mockito.eq(HttpMethod.POST),
                Mockito.any(),
                Mockito.eq(ClassifyResponseDto.class)))
                .thenThrow(new RestClientException("timeout"));

        // Constructor with injected RestTemplate simulates timeouts without real HTTP.
        ClassifierServiceClient c = new ClassifierServiceClient(
                ClassifierClientTestSupport.defaultBaseUrl(),
                "default",
                1,
                throwingRestTemplate
        );

        assertNull(c.classifyWithText("any query"));
    }

    @Test
    void classify_returnsNull_whenBaseUrlEmpty() {
        ClassifierServiceClient noUrl = new ClassifierServiceClient("", "default", 5000, restTemplate);

        QueryType result = noUrl.classify("any query");

        assertNull(result);
    }
}

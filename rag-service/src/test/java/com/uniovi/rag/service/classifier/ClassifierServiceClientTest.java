package com.uniovi.rag.service.classifier;

import com.uniovi.rag.model.QueryType;
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
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        classifier = new ClassifierServiceClient("http://localhost:8000", "default", 5000, restTemplate);
    }

    @Test
    void classify_returnsQueryType_whenServiceReturns200WithValidQueryType() {
        server.expect(requestTo("http://localhost:8000/classify"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"query\":\"How many documents?\",\"modelId\":\"default\"}"))
                .andRespond(withSuccess("{\"queryType\": \"COUNT_DOCUMENTS\"}", MediaType.APPLICATION_JSON));

        QueryType result = classifier.classify("How many documents?");

        server.verify();
        assertEquals(QueryType.COUNT_DOCUMENTS, result);
    }

    @Test
    void classifyWithText_returnsString_whenServiceReturns200() {
        server.expect(requestTo("http://localhost:8000/classify"))
                .andRespond(withSuccess("{\"queryType\": \"FIND_PARAGRAPH\"}", MediaType.APPLICATION_JSON));

        String result = classifier.classifyWithText("Find the paragraph about X");

        server.verify();
        assertEquals("FIND_PARAGRAPH", result);
    }

    @Test
    void classify_returnsNull_whenServiceReturns5xx() {
        server.expect(requestTo("http://localhost:8000/classify"))
                .andRespond(withServerError());

        QueryType result = classifier.classify("any query");

        server.verify();
        assertNull(result);
    }

    @Test
    void classify_returnsNull_whenServiceReturnsInvalidQueryType() {
        server.expect(requestTo("http://localhost:8000/classify"))
                .andRespond(withSuccess("{\"queryType\": \"NOT_A_REAL_ENUM\"}", MediaType.APPLICATION_JSON));

        QueryType result = classifier.classify("any query");

        server.verify();
        assertNull(result);
    }

    @Test
    void classify_returnsNull_whenServiceReturnsNon2xx() {
        server.expect(requestTo("http://localhost:8000/classify"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        QueryType result = classifier.classify("any query");

        server.verify();
        assertNull(result);
    }

    @Test
    void classifyWithText_returnsNull_whenRestTemplateThrowsRestClientException_timeout() {
        RestTemplate throwingRestTemplate = mock(RestTemplate.class);
        when(throwingRestTemplate.postForEntity(
                Mockito.anyString(),
                Mockito.any(),
                Mockito.eq(ClassifyResponseDto.class)))
                .thenThrow(new RestClientException("timeout"));

        // Nota: constructor package-private permite inyectar RestTemplate para simular timeouts.
        ClassifierServiceClient c = new ClassifierServiceClient(
                "http://localhost:8000",
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

package com.uniovi.rag.service.classifier;

import com.uniovi.rag.model.QueryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
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
    void classify_returnsNull_whenBaseUrlEmpty() {
        ClassifierServiceClient noUrl = new ClassifierServiceClient("", "default", 5000, restTemplate);

        QueryType result = noUrl.classify("any query");

        assertNull(result);
    }
}

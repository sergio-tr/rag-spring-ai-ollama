package com.uniovi.rag.infrastructure.classifier;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.port.ClassifierTrainBytesCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ClassifierLabClientTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private ClassifierLabClient client;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client =
                new ClassifierLabClient(
                        "http://localhost:8888/",
                        5000,
                        240_000,
                        new ObjectMapper(),
                        restTemplate,
                        restTemplate);
    }

    @Test
    void constructor_stripsTrailingSlashesFromBaseUrl() throws Exception {
        ClassifierLabClient c =
                new ClassifierLabClient(
                        "http://example.test///",
                        1000,
                        240_000,
                        new ObjectMapper(),
                        restTemplate,
                        restTemplate);
        var f = ClassifierLabClient.class.getDeclaredField("baseUrl");
        f.setAccessible(true);
        assertThat(f.get(c)).isEqualTo("http://example.test");
    }

    @Test
    void isConfigured_falseWhenBaseUrlBlank() {
        ClassifierLabClient empty =
                new ClassifierLabClient("", 5000, 240_000, new ObjectMapper(), restTemplate, restTemplate);
        assertThat(empty.isConfigured()).isFalse();
    }

    @Test
    void trainBytes_requiresNonEmptyFile() {
        assertThatThrownBy(
                        () ->
                                client.trainBytes(
                                        new ClassifierTrainBytesCommand(
                                                null, "d.xlsx", "m", null, null, null, 1, 8)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void trainBytes_notConfigured_throws503() {
        ClassifierLabClient noUrl =
                new ClassifierLabClient("", 5000, 240_000, new ObjectMapper(), restTemplate, restTemplate);
        assertThatThrownBy(
                        () ->
                                noUrl.trainBytes(
                                        new ClassifierTrainBytesCommand(
                                                new byte[] {1}, "d.xlsx", "m", null, null, null, 1, 8)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void trainBytes_postsMultipartAndReturnsBody() {
        server.expect(requestTo("http://localhost:8888/train"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"job\":\"ok\"}", MediaType.APPLICATION_JSON));

        Map<String, Object> body =
                client.trainBytes(
                        new ClassifierTrainBytesCommand(
                                new byte[] {1, 2}, "d.xlsx", "my", null, null, null, 3, 16));

        server.verify();
        assertThat(body).containsEntry("job", "ok");
    }

    @Test
    void evaluateBytes_withoutFile_postsEmptyEntity() {
        server.expect(requestTo("http://localhost:8888/evaluate?includeImages=false&modelId=m1"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"acc\":1}", MediaType.APPLICATION_JSON));

        Map<String, Object> body = client.evaluateBytes("m1", false, null, "x.xlsx");

        server.verify();
        assertThat(body).containsEntry("acc", 1);
    }

    @Test
    void evaluateBytes_withFile_postsMultipart() {
        server.expect(requestTo("http://localhost:8888/evaluate?includeImages=true"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.evaluateBytes(null, true, new byte[] {9}, "e.xlsx");

        server.verify();
    }

    @Test
    void classify_postsJsonPayload() {
        server.expect(requestTo("http://localhost:8888/classify"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"query\":\"q1\",\"modelId\":\"mid\"}"))
                .andRespond(withSuccess("{\"label\":\"x\"}", MediaType.APPLICATION_JSON));

        Map<String, Object> out = client.classify("q1", "mid");

        server.verify();
        assertThat(out).containsEntry("label", "x");
    }

    @Test
    void trainBytes_mapsErrorBodyMessage() {
        server.expect(requestTo("http://localhost:8888/train"))
                .andRespond(
                        withStatus(HttpStatus.BAD_REQUEST)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body("{\"error\":{\"message\":\"bad dataset\"}}"));

        assertThatThrownBy(
                        () ->
                                client.trainBytes(
                                        new ClassifierTrainBytesCommand(
                                                new byte[] {1}, "d.xlsx", "m", null, null, null, 1, 1)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex -> assertThat(((ResponseStatusException) ex).getBody().getDetail()).contains("bad dataset"));
        server.verify();
    }

    @Test
    void trainBytes_returnsEmptyMap_whenBodyNull() {
        server.expect(requestTo("http://localhost:8888/train"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());

        Map<String, Object> body =
                client.trainBytes(
                        new ClassifierTrainBytesCommand(
                                new byte[] {1}, "d.xlsx", "m", null, null, null, 1, 1));

        server.verify();
        assertThat(body).isEmpty();
    }

    @Test
    void classify_networkFailure_mapsTo502() {
        server.expect(requestTo("http://localhost:8888/classify"))
                .andRespond(request -> {
                    throw new ResourceAccessException("timeout");
                });

        assertThatThrownBy(() -> client.classify("q", "m")).isInstanceOf(ResponseStatusException.class);

        server.verify();
    }

    @Test
    void classify_mapsNonJsonErrorToStatusText() {
        server.expect(requestTo("http://localhost:8888/classify"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY).body("plain"));

        assertThatThrownBy(() -> client.classify("q", "m"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex ->
                                assertThat(((ResponseStatusException) ex).getBody().getDetail())
                                        .isNotBlank());
        server.verify();
    }
}

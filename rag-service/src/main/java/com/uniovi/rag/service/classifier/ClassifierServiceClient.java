package com.uniovi.rag.service.classifier;

import com.uniovi.rag.model.QueryType;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/** Response DTO for classifier-service POST /classify (camelCase). */
record ClassifyResponseDto(String queryType) {}

/**
 * Query classifier that calls the classifier-service API over HTTP.
 * Uses the default pre-trained model (tag "default") so RAG classification is consistent.
 * Request/response use camelCase for interoperability.
 * POST {baseUrl}/classify with body {"query": "...", "modelId": "default"}, expects {"queryType": "COUNT_DOCUMENTS"} etc.
 * On failure or non-2xx returns null so LLM fallback can be used.
 */
public class ClassifierServiceClient implements QueryClassifier {

    private static final String DEFAULT_MODEL_ID = "default";

    private final String baseUrl;
    private final String modelId;
    private final RestTemplate restTemplate;

    public ClassifierServiceClient(String baseUrl) {
        this(baseUrl, DEFAULT_MODEL_ID, 5000);
    }

    public ClassifierServiceClient(String baseUrl, int timeoutMs) {
        this(baseUrl, DEFAULT_MODEL_ID, timeoutMs);
    }

    /** Main constructor: base URL of classifier-service, model tag (use "default" for pre-trained), timeout. */
    public ClassifierServiceClient(String baseUrl, String modelId, int timeoutMs) {
        this(baseUrl, modelId, timeoutMs, createDefaultRestTemplate(timeoutMs));
    }

    /** Constructor for testing: allows injecting a custom RestTemplate (e.g. with MockRestServiceServer). */
    ClassifierServiceClient(String baseUrl, String modelId, int timeoutMs, RestTemplate restTemplate) {
        this.baseUrl = baseUrl != null ? baseUrl.replaceAll("/+$", "") : "";
        this.modelId = (modelId != null && !modelId.isBlank()) ? modelId : DEFAULT_MODEL_ID;
        int effectiveTimeout = timeoutMs > 0 ? timeoutMs : 5000;
        this.restTemplate = restTemplate != null ? restTemplate : createDefaultRestTemplate(effectiveTimeout);
    }

    @Override
    public QueryType classify(String query) {
        String raw = classifyWithText(query);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            QueryType qt = QueryType.valueOf(raw.trim());
            log().info("[CLASSIFIER] Query type: {}", qt);
            return qt;
        } catch (IllegalArgumentException e) {
            log().warn("[CLASSIFIER] Unknown queryType: '{}', returning null", raw);
            return null;
        }
    }

    @Override
    public String classifyWithText(String query) {
        if (baseUrl.isEmpty()) {
            log().debug("[CLASSIFIER] Classifier-service URL not configured, returning null");
            return null;
        }
        if (query == null) {
            return null;
        }
        String url = baseUrl + "/classify";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> body = Map.of("query", query, "modelId", modelId);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<ClassifyResponseDto> response = restTemplate.postForEntity(url, request, ClassifyResponseDto.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                ClassifyResponseDto responseBody = response.getBody();
                return (responseBody != null && responseBody.queryType() != null) ? responseBody.queryType() : null;
            }
        } catch (RestClientException e) {
            log().warn("[CLASSIFIER] Error calling classifier-service (LLM fallback will be used): {}", e.getMessage());
        }
        return null;
    }

    private static RestTemplate createDefaultRestTemplate(int timeoutMs) {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(java.time.Duration.ofMillis(timeoutMs));
        RestTemplate rt = new RestTemplate();
        rt.setRequestFactory(factory);
        return rt;
    }
}

package com.uniovi.rag.infrastructure.classifier;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.port.ClassifierLabPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * HTTP client for classifier-service lab endpoints ({@code /train}, {@code /evaluate}).
 * Used by {@code /api/v5/lab/classifier/*} so the browser never calls Python directly.
 */
@Service
public class ClassifierLabClient implements ClassifierLabPort {

    private static final Logger log = LoggerFactory.getLogger(ClassifierLabClient.class);

    private final String baseUrl;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public ClassifierLabClient(
            @Value("${rag.classifier.service.url:}") String baseUrl,
            @Value("${rag.classifier.service.timeout-ms:5000}") int timeoutMs,
            ObjectMapper objectMapper) {
        this(baseUrl, timeoutMs, objectMapper, null);
    }

    /**
     * Package-private hook for tests: inject a {@link RestTemplate} (e.g. bound to {@code MockRestServiceServer}).
     */
    ClassifierLabClient(String baseUrl, int timeoutMs, ObjectMapper objectMapper, RestTemplate restTemplate) {
        this.baseUrl = baseUrl != null ? baseUrl.replaceAll("/+$", "") : "";
        this.objectMapper = objectMapper;
        int effective = timeoutMs > 0 ? timeoutMs : 5000;
        this.restTemplate = restTemplate != null ? restTemplate : createRestTemplate(effective);
    }

    private static RestTemplate createRestTemplate(int timeoutMs) {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(java.time.Duration.ofMillis(timeoutMs));
        RestTemplate rt = new RestTemplate();
        rt.setRequestFactory(factory);
        return rt;
    }

    public Map<String, Object> train(
            MultipartFile file,
            String modelName,
            String labelsJson,
            MultipartFile labelsFile,
            int epochs,
            int batchSize) {
        requireConfigured();
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Training file is required");
        }
        try {
            byte[] labelsBytes =
                    labelsFile != null && !labelsFile.isEmpty() ? labelsFile.getBytes() : null;
            String labelsFilename =
                    labelsFile != null && labelsFile.getOriginalFilename() != null
                            ? labelsFile.getOriginalFilename()
                            : "labels.txt";
            return trainBytes(
                    file.getBytes(),
                    file.getOriginalFilename() != null ? file.getOriginalFilename() : "dataset.xlsx",
                    modelName,
                    labelsJson,
                    labelsBytes,
                    labelsBytes != null ? labelsFilename : null,
                    epochs,
                    batchSize);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read upload: " + e.getMessage());
        }
    }

    /**
     * Same as multipart {@link #train} but for background jobs that materialized uploads to byte arrays.
     */
    public Map<String, Object> trainBytes(
            byte[] fileContent,
            String datasetFilename,
            String modelName,
            String labelsJson,
            byte[] labelsFileContent,
            String labelsFilename,
            int epochs,
            int batchSize) {
        requireConfigured();
        if (fileContent == null || fileContent.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Training file is required");
        }
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add(
                    "file",
                    new NamedByteArrayResource(
                            fileContent,
                            datasetFilename != null ? datasetFilename : "dataset.xlsx"));
            body.add("model_name", modelName != null ? modelName : "model");
            if (labelsJson != null && !labelsJson.isBlank()) {
                body.add("labels", labelsJson);
            }
            if (labelsFileContent != null && labelsFileContent.length > 0) {
                body.add(
                        "labels_file",
                        new NamedByteArrayResource(
                                labelsFileContent,
                                labelsFilename != null ? labelsFilename : "labels.txt"));
            }
            body.add("epochs", Integer.toString(epochs));
            body.add("batch_size", Integer.toString(batchSize));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> resp =
                    restTemplate.postForEntity(baseUrl + "/train", entity, Map.class);
            return bodyAsMap(resp);
        } catch (HttpStatusCodeException e) {
            throw mapClassifierError(e);
        } catch (RestClientException e) {
            log.warn("Classifier train failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Classifier service error");
        }
    }

    public Map<String, Object> evaluate(String modelId, boolean includeImages, MultipartFile datasetFile) {
        try {
            byte[] bytes = null;
            String name = "eval.xlsx";
            if (datasetFile != null && !datasetFile.isEmpty()) {
                bytes = datasetFile.getBytes();
                if (datasetFile.getOriginalFilename() != null) {
                    name = datasetFile.getOriginalFilename();
                }
            }
            return evaluateBytes(modelId, includeImages, bytes, name);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read upload: " + e.getMessage());
        }
    }

    public Map<String, Object> evaluateBytes(
            String modelId, boolean includeImages, byte[] datasetContent, String datasetFilename) {
        requireConfigured();
        try {
            StringBuilder url = new StringBuilder(baseUrl).append("/evaluate?includeImages=").append(includeImages);
            if (modelId != null && !modelId.isBlank()) {
                url.append("&modelId=").append(URLEncoder.encode(modelId.trim(), StandardCharsets.UTF_8));
            }
            if (datasetContent != null && datasetContent.length > 0) {
                MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
                body.add(
                        "file",
                        new NamedByteArrayResource(
                                datasetContent,
                                datasetFilename != null ? datasetFilename : "eval.xlsx"));
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.MULTIPART_FORM_DATA);
                HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
                ResponseEntity<Map> resp =
                        restTemplate.exchange(url.toString(), HttpMethod.POST, entity, Map.class);
                return bodyAsMap(resp);
            }
            ResponseEntity<Map> resp =
                    restTemplate.exchange(url.toString(), HttpMethod.POST, HttpEntity.EMPTY, Map.class);
            return bodyAsMap(resp);
        } catch (HttpStatusCodeException e) {
            throw mapClassifierError(e);
        } catch (RestClientException e) {
            log.warn("Classifier evaluate failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Classifier service error");
        }
    }

    public Map<String, Object> classify(String query, String modelId) {
        requireConfigured();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, String> payload =
                    Map.of("query", query != null ? query : "", "modelId", modelId != null ? modelId : "default");
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(payload, headers);
            ResponseEntity<Map> resp = restTemplate.postForEntity(baseUrl + "/classify", entity, Map.class);
            return bodyAsMap(resp);
        } catch (HttpStatusCodeException e) {
            throw mapClassifierError(e);
        } catch (RestClientException e) {
            log.warn("Classifier classify failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Classifier service error");
        }
    }

    public boolean isConfigured() {
        return !baseUrl.isEmpty();
    }

    private void requireConfigured() {
        if (baseUrl.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Classifier service URL is not configured");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> bodyAsMap(ResponseEntity<Map> resp) {
        Map<?, ?> body = resp.getBody();
        if (body == null) {
            return Map.of();
        }
        return (Map<String, Object>) body;
    }

    private ResponseStatusException mapClassifierError(HttpStatusCodeException e) {
        String msg = "Classifier request failed";
        try {
            Map<?, ?> errBody = objectMapper.readValue(e.getResponseBodyAsString(), Map.class);
            if (errBody != null && errBody.containsKey("error") && errBody.get("error") instanceof Map<?, ?> errMap) {
                Object m = errMap.get("message");
                if (m != null) {
                    msg = m.toString();
                }
            }
        } catch (Exception ignored) {
            msg = e.getStatusText();
        }
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.BAD_GATEWAY;
        }
        return new ResponseStatusException(status, msg);
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        NamedByteArrayResource(byte[] data, String filename) {
            super(data);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}

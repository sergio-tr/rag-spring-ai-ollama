package com.uniovi.rag.infrastructure.classifier;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.port.ClassifierLabPort;
import com.uniovi.rag.application.port.ClassifierTrainBytesCommand;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * HTTP client for classifier-service lab endpoints ({@code /train}, {@code /evaluate}).
 * Used by {@code /api/v5/lab/classifier/*} so the browser never calls Python directly.
 */
@Service
public class ClassifierLabClient implements ClassifierLabPort {

    private static final Logger log = LoggerFactory.getLogger(ClassifierLabClient.class);

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_OF_STRING_OBJECT =
            new ParameterizedTypeReference<>() {};

    private final String baseUrl;
    private final RestTemplate labRestTemplate;
    private final RestTemplate classifyRestTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public ClassifierLabClient(
            @Value("${rag.classifier.service.url:}") String baseUrl,
            @Value("${rag.classifier.service.timeout-ms:5000}") int classifyTimeoutMs,
            @Value("${rag.classifier.service.lab-timeout-ms:240000}") int labTimeoutMs,
            ObjectMapper objectMapper) {
        this(baseUrl, classifyTimeoutMs, labTimeoutMs, objectMapper, null, null);
    }

    /**
     * Package-private hook for tests: inject a {@link RestTemplate} (e.g. bound to {@code MockRestServiceServer}).
     */
    ClassifierLabClient(
            String baseUrl,
            int classifyTimeoutMs,
            int labTimeoutMs,
            ObjectMapper objectMapper,
            RestTemplate labRestTemplate,
            RestTemplate classifyRestTemplate) {
        this.baseUrl = baseUrl != null ? stripTrailingSlashes(baseUrl) : "";
        this.objectMapper = objectMapper;
        int effectiveLab = labTimeoutMs > 0 ? labTimeoutMs : 240_000;
        int effectiveClassify = classifyTimeoutMs > 0 ? classifyTimeoutMs : 5_000;
        this.labRestTemplate = labRestTemplate != null ? labRestTemplate : createRestTemplate(effectiveLab);
        this.classifyRestTemplate =
                classifyRestTemplate != null ? classifyRestTemplate : createRestTemplate(effectiveClassify);
    }

    private static RestTemplate createRestTemplate(int timeoutMs) {
        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        RestTemplate rt = new RestTemplate();
        rt.setRequestFactory(factory);
        return rt;
    }

    private static String stripTrailingSlashes(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        int end = url.length();
        while (end > 0 && url.charAt(end - 1) == '/') {
            end--;
        }
        return url.substring(0, end);
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
                    new ClassifierTrainBytesCommand(
                            file.getBytes(),
                            file.getOriginalFilename() != null ? file.getOriginalFilename() : "dataset.xlsx",
                            modelName,
                            labelsJson,
                            labelsBytes,
                            labelsBytes != null ? labelsFilename : null,
                            epochs,
                            batchSize));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read upload: " + e.getMessage());
        }
    }

    /**
     * Same as multipart {@link #train} but for background jobs that materialized uploads to byte arrays.
     */
    @Override
    public Map<String, Object> trainBytes(ClassifierTrainBytesCommand cmd) {
        byte[] fileContent = cmd.fileContent();
        String datasetFilename = cmd.datasetFilename();
        String modelName = cmd.modelName();
        String labelsJson = cmd.labelsJson();
        byte[] labelsFileContent = cmd.labelsFileContent();
        String labelsFilename = cmd.labelsFilename();
        int epochs = cmd.epochs();
        int batchSize = cmd.batchSize();
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
            if (cmd.trainArtifactOwnerId() != null && !cmd.trainArtifactOwnerId().isBlank()) {
                body.add("owner_id", cmd.trainArtifactOwnerId().trim());
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map<String, Object>> resp =
                    labRestTemplate.exchange(baseUrl + "/train", HttpMethod.POST, entity, MAP_OF_STRING_OBJECT);
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
                ResponseEntity<Map<String, Object>> resp =
                        labRestTemplate.exchange(url.toString(), HttpMethod.POST, entity, MAP_OF_STRING_OBJECT);
                return bodyAsMap(resp);
            }
            ResponseEntity<Map<String, Object>> resp =
                    labRestTemplate.exchange(url.toString(), HttpMethod.POST, HttpEntity.EMPTY, MAP_OF_STRING_OBJECT);
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
            ResponseEntity<Map<String, Object>> resp =
                    classifyRestTemplate.exchange(baseUrl + "/classify", HttpMethod.POST, entity, MAP_OF_STRING_OBJECT);
            return bodyAsMap(resp);
        } catch (HttpStatusCodeException e) {
            log.warn(
                    "Classifier /classify failed status={} url={} body={}",
                    e.getStatusCode().value(),
                    baseUrl + "/classify",
                    safeBodyPreview(e.getResponseBodyAsString()));
            throw mapClassifierError(e);
        } catch (RestClientException e) {
            log.warn("Classifier classify failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Classifier service error");
        }
    }

    @Override
    public List<Map<String, Object>> listModels() {
        requireConfigured();
        try {
            ResponseEntity<List> resp =
                    classifyRestTemplate.exchange(baseUrl + "/models", HttpMethod.GET, HttpEntity.EMPTY, List.class);
            Object body = resp.getBody();
            if (body instanceof List<?> list) {
                List<Map<String, Object>> out = new ArrayList<>();
                for (Object row : list) {
                    if (row instanceof Map<?, ?> m) {
                        //noinspection unchecked
                        out.add((Map<String, Object>) m);
                    }
                }
                return out;
            }
            return List.of();
        } catch (HttpStatusCodeException e) {
            log.warn(
                    "Classifier /models failed status={} url={} body={}",
                    e.getStatusCode().value(),
                    baseUrl + "/models",
                    safeBodyPreview(e.getResponseBodyAsString()));
            throw mapClassifierError(e);
        } catch (RestClientException e) {
            log.warn("Classifier list models failed: {}", e.getMessage());
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

    private Map<String, Object> bodyAsMap(ResponseEntity<Map<String, Object>> resp) {
        Map<String, Object> body = resp.getBody();
        if (body == null) {
            return Map.of();
        }
        return body;
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

    private static String safeBodyPreview(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.trim();
        if (t.length() <= 500) {
            return t;
        }
        return t.substring(0, 500) + "…";
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

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof NamedByteArrayResource that)) {
                return false;
            }
            return super.equals(o) && Objects.equals(filename, that.filename);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), filename);
        }
    }
}

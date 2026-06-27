package com.uniovi.rag.infrastructure.llm.openaicompat;

import com.uniovi.rag.infrastructure.llm.LlmUrlUtils;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Objects;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * Low-level {@code POST /v1/chat/completions} transport. Does not call {@code /v1/models}, {@code /health}, or Ollama routes.
 */
@Component
class OpenAiCompatibleChatCompletionsHttpClient {

    OpenAiChatCompletionResponse post(String baseUrl, String apiKey, OpenAiChatCompletionRequest body, long timeoutMs) {
        Objects.requireNonNull(body, "body");
        if (baseUrl == null || baseUrl.isBlank()) {
            throw OpenAiCompatibleLlmException.missingConfiguration("default-base-url is blank");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw OpenAiCompatibleLlmException.missingConfiguration("resolved API key is blank");
        }
        String completionsUrl = LlmUrlUtils.openAiChatCompletionsUrl(baseUrl);
        RestTemplate restTemplate = restTemplateFor(timeoutMs);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        HttpEntity<OpenAiChatCompletionRequest> entity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<OpenAiChatCompletionResponse> response =
                    restTemplate.exchange(completionsUrl, HttpMethod.POST, entity, OpenAiChatCompletionResponse.class);
            OpenAiChatCompletionResponse responseBody = response.getBody();
            if (responseBody == null) {
                throw OpenAiCompatibleLlmException.invalidResponse("empty HTTP body");
            }
            return responseBody;
        } catch (HttpStatusCodeException e) {
            throw OpenAiCompatibleChatMapper.mapHttpError(
                    e.getStatusCode().value(), e.getResponseBodyAsString(), completionsUrl);
        } catch (ResourceAccessException e) {
            if (isTimeout(e)) {
                throw OpenAiCompatibleLlmException.timeout(e);
            }
            if (isConnectionFailure(e)) {
                throw OpenAiCompatibleLlmException.connectionFailed(e);
            }
            throw OpenAiCompatibleLlmException.connectionFailed(e);
        }
    }

    private static RestTemplate restTemplateFor(long timeoutMs) {
        long effective = Math.max(1_000L, timeoutMs);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(effective));
        factory.setReadTimeout(Duration.ofMillis(effective));
        return new RestTemplate(factory);
    }

    private static boolean isTimeout(ResourceAccessException e) {
        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause instanceof SocketTimeoutException) {
                return true;
            }
            String message = cause.getMessage();
            if (message != null && message.toLowerCase().contains("timed out")) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private static boolean isConnectionFailure(ResourceAccessException e) {
        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause instanceof ConnectException) {
                return true;
            }
            cause = cause.getCause();
        }
        String message = e.getMessage();
        return message != null && message.toLowerCase().contains("connection refused");
    }
}

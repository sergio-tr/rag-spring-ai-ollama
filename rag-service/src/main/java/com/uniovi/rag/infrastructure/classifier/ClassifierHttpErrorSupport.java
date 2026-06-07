package com.uniovi.rag.infrastructure.classifier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;

/** Parses classifier-service HTTP failures and classifies them for runtime fallback. */
final class ClassifierHttpErrorSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ClassifierHttpErrorSupport() {}

    static ClassifierCallException fromHttpStatus(int status, String bodyPreview, String url, Throwable cause) {
        String normalizedBody = bodyPreview != null ? bodyPreview.trim() : "";
        if (isUvicornProtocolRejection(status, normalizedBody)) {
            return new ClassifierCallException(
                    ClassifierCallException.Kind.UNAVAILABLE,
                    "Classifier HTTP protocol error status="
                            + status
                            + " url="
                            + url
                            + " detail=invalid_or_non_http_request",
                    status,
                    cause);
        }
        ParsedError parsed = parseStructuredError(normalizedBody);
        if (status == 400 || status == 422) {
            if (parsed.code() != null && parsed.code().contains("VALIDATION")) {
                return new ClassifierCallException(
                        ClassifierCallException.Kind.INVALID_REQUEST,
                        "Classifier request rejected status=" + status + " url=" + url + " code=" + parsed.code(),
                        status,
                        cause);
            }
            return new ClassifierCallException(
                    ClassifierCallException.Kind.INVALID_REQUEST,
                    "Classifier request rejected status=" + status + " url=" + url,
                    status,
                    cause);
        }
        if (status == 503 && normalizedBody.contains("Invalid classifier output")) {
            return new ClassifierCallException(
                    ClassifierCallException.Kind.INVALID_OUTPUT,
                    "Classifier invalid output status=503 url=" + url,
                    status,
                    cause);
        }
        if (status >= 500 || status == 404) {
            return new ClassifierCallException(
                    ClassifierCallException.Kind.UNAVAILABLE,
                    "Classifier service unavailable status=" + status + " url=" + url,
                    status,
                    cause);
        }
        return new ClassifierCallException(
                ClassifierCallException.Kind.UNAVAILABLE,
                "Classifier HTTP error status=" + status + " url=" + url,
                status,
                cause);
    }

    static ClassifierCallException fromTransport(String url, Throwable cause) {
        if (isTimeout(cause)) {
            return new ClassifierCallException(
                    ClassifierCallException.Kind.TIMEOUT,
                    "Classifier timeout url=" + url + " detail=" + safeMessage(cause),
                    0,
                    cause);
        }
        return new ClassifierCallException(
                ClassifierCallException.Kind.UNAVAILABLE,
                "Classifier transport error url=" + url + " detail=" + safeMessage(cause),
                0,
                cause);
    }

    static boolean isUvicornProtocolRejection(int status, String body) {
        if (status != 400 || body == null || body.isBlank()) {
            return false;
        }
        String lower = body.toLowerCase(Locale.ROOT);
        return lower.contains("invalid http request received")
                || lower.contains("unsupported upgrade request");
    }

    private static boolean isTimeout(Throwable cause) {
        if (cause == null) {
            return false;
        }
        String msg = safeMessage(cause).toLowerCase(Locale.ROOT);
        if (msg.contains("timeout") || msg.contains("timed out")) {
            return true;
        }
        Throwable root = cause;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
            String rootMsg = safeMessage(root).toLowerCase(Locale.ROOT);
            if (rootMsg.contains("timeout") || rootMsg.contains("timed out")) {
                return true;
            }
        }
        return false;
    }

    private static ParsedError parseStructuredError(String body) {
        if (body == null || body.isBlank() || !body.startsWith("{")) {
            return ParsedError.empty();
        }
        try {
            JsonNode root = MAPPER.readTree(body);
            JsonNode error = root.path("error");
            if (error.isMissingNode() || error.isNull()) {
                error = root;
            }
            String code = textOrNull(error.get("code"));
            String message = textOrNull(error.get("message"));
            return new ParsedError(code, message);
        } catch (Exception ignored) {
            return ParsedError.empty();
        }
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String t = node.asText(null);
        return t != null && !t.isBlank() ? t.trim() : null;
    }

    private static String safeMessage(Throwable cause) {
        String m = cause.getMessage();
        return m != null && !m.isBlank() ? m.trim() : cause.getClass().getSimpleName();
    }

    private record ParsedError(String code, String message) {
        static ParsedError empty() {
            return new ParsedError(null, null);
        }
    }
}

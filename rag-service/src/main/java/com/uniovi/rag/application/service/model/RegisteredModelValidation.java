package com.uniovi.rag.application.service.model;

import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Shared semantics for registered classifier models ({@code classifier_model}).
 * Name = human unique app identifier; inference tag = classifier-service model id ({@code artifact_path}).
 */
public final class RegisteredModelValidation {

    public static final String MODEL_NAME_REQUIRED = "MODEL_NAME_REQUIRED";
    public static final String MODEL_NAME_DUPLICATE = "MODEL_NAME_DUPLICATE";
    public static final String MODEL_NAME_RESERVED = "MODEL_NAME_RESERVED";
    public static final String INFERENCE_TAG_REQUIRED = "INFERENCE_TAG_REQUIRED";
    public static final String INFERENCE_TAG_DUPLICATE = "INFERENCE_TAG_DUPLICATE";
    public static final String INFERENCE_TAG_RESERVED = "INFERENCE_TAG_RESERVED";
    public static final String DEFAULT_MODEL_POINTER_INVALID = "DEFAULT_MODEL_POINTER_INVALID";

    private static final Set<String> RESERVED =
            Set.of("default", "null", "none", "undefined", "n/a");

    private RegisteredModelValidation() {
    }

    public static String normalizeName(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim();
    }

    public static String normalizeInferenceTag(String raw) {
        return normalizeName(raw);
    }

    public static boolean isReservedToken(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return RESERVED.contains(value.trim().toLowerCase(Locale.ROOT));
    }

    public static void assertValidName(String rawName) {
        String name = normalizeName(rawName);
        if (name.isEmpty()) {
            throw badRequest(MODEL_NAME_REQUIRED, "Model name is required");
        }
        if (isReservedToken(name)) {
            throw badRequest(MODEL_NAME_RESERVED, "Model name '" + name + "' is reserved");
        }
    }

    /**
     * @param allowSystemDefaultTag when true, permits inference tag {@code default} (built-in classifier-service tag)
     */
    public static void assertValidInferenceTag(String rawTag, boolean allowSystemDefaultTag) {
        String tag = normalizeInferenceTag(rawTag);
        if (tag.isEmpty()) {
            throw badRequest(INFERENCE_TAG_REQUIRED, "Inference tag is required");
        }
        if (!allowSystemDefaultTag && isReservedToken(tag)) {
            throw badRequest(INFERENCE_TAG_RESERVED, "Inference tag '" + tag + "' is reserved");
        }
    }

    public static void assertNoDuplicateName(boolean exists) {
        if (exists) {
            throw conflict(MODEL_NAME_DUPLICATE, "Another registered model already uses this name");
        }
    }

    public static void assertNoDuplicateInferenceTag(boolean exists) {
        if (exists) {
            throw conflict(INFERENCE_TAG_DUPLICATE, "Another registered model already uses this inference tag");
        }
    }

    public static void assertDefaultPointerValid(boolean targetExists) {
        if (!targetExists) {
            throw badRequest(DEFAULT_MODEL_POINTER_INVALID, "Default model pointer references an unknown model");
        }
    }

    private static ResponseStatusException badRequest(String code, String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, code + ": " + message);
    }

    private static ResponseStatusException conflict(String code, String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, code + ": " + message);
    }
}

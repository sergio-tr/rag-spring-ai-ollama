package com.uniovi.rag.application.service.runtime.traceregressionsuiterunimport;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

/**
 * Shared, ordering-sensitive manifest validation primitives for P43/P53 run ZIP manifests.
 *
 * <p>Important: callers must preserve validation ordering if exception precedence is part of contract.
 */
public final class RuntimeTraceRegressionSuiteRunImportManifestValidators {

    public static final String FIELD_RUN_ID = "runId";
    public static final String FIELD_DEFINITION_ID = "definitionId";
    public static final String FIELD_SOURCE_TYPE = "sourceType";
    public static final String FIELD_SUITE_OUTCOME = "suiteOutcome";

    private RuntimeTraceRegressionSuiteRunImportManifestValidators() {}

    public static void requireExportKindRegressionSuiteRun(JsonNode root, RuntimeException invalidManifest) {
        requireTextEquals(root, "exportKind", "REGRESSION_SUITE_RUN", invalidManifest);
    }

    public static void requireSchemaVersion1(JsonNode root, RuntimeException invalidManifest) {
        requireIntEquals(root, "schemaVersion", 1, invalidManifest);
    }

    public static void requireTruncatedFalse(JsonNode root, RuntimeException invalidManifest) {
        requireBooleanEquals(root, "truncated", false, invalidManifest);
    }

    public static void requireZipSizeBytes(JsonNode root, int bodyLength, RuntimeException invalidManifest) {
        requireLongEquals(root, "zipSizeBytes", bodyLength, invalidManifest);
    }

    public static void requireSelectorType(JsonNode root, String selectorType, RuntimeException invalidManifest) {
        requireTextEquals(root, "selectorType", selectorType, invalidManifest);
    }

    public static JsonNode requireObject(JsonNode root, String field, RuntimeException invalidManifest) {
        if (!root.has(field) || !root.get(field).isObject()) {
            throw invalidManifest;
        }
        return root.get(field);
    }

    public static UUID requireUuidText(JsonNode root, String field, RuntimeException invalidManifest) {
        String s = requireText(root, field, invalidManifest);
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException ex) {
            throw invalidManifest;
        }
    }

    public static UUID requireUuidTextInObject(JsonNode obj, String field, RuntimeException invalidManifest) {
        if (obj == null || !obj.hasNonNull(field) || !obj.get(field).isTextual()) {
            throw invalidManifest;
        }
        try {
            return UUID.fromString(obj.get(field).asText());
        } catch (IllegalArgumentException ex) {
            throw invalidManifest;
        }
    }

    public static UUID optionalUuidTextOrNull(JsonNode root, String field, RuntimeException invalidManifest) {
        if (!root.has(field)) {
            throw invalidManifest;
        }
        JsonNode n = root.get(field);
        if (n == null || n.isNull()) {
            return null;
        }
        if (!n.isTextual()) {
            throw invalidManifest;
        }
        try {
            return UUID.fromString(n.asText());
        } catch (IllegalArgumentException ex) {
            throw invalidManifest;
        }
    }

    public static String requireText(JsonNode root, String field, RuntimeException invalidManifest) {
        if (!root.hasNonNull(field) || !root.get(field).isTextual()) {
            throw invalidManifest;
        }
        return root.get(field).asText();
    }

    public static void requireTextEquals(
            JsonNode root, String field, String expected, RuntimeException invalidManifest) {
        String s = requireText(root, field, invalidManifest);
        if (!expected.equals(s)) {
            throw invalidManifest;
        }
    }

    public static void requireIntEquals(
            JsonNode root, String field, int expected, RuntimeException invalidManifest) {
        if (!root.has(field) || !root.get(field).isIntegralNumber() || root.get(field).intValue() != expected) {
            throw invalidManifest;
        }
    }

    public static void requireLongEquals(
            JsonNode root, String field, long expected, RuntimeException invalidManifest) {
        if (!root.has(field) || !root.get(field).isIntegralNumber() || root.get(field).longValue() != expected) {
            throw invalidManifest;
        }
    }

    public static void requireBooleanEquals(
            JsonNode root, String field, boolean expected, RuntimeException invalidManifest) {
        if (!root.has(field) || !root.get(field).isBoolean() || root.get(field).booleanValue() != expected) {
            throw invalidManifest;
        }
    }

    public static boolean hasIntegralCount(JsonNode root, String field) {
        return root.has(field) && root.get(field).isIntegralNumber();
    }
}


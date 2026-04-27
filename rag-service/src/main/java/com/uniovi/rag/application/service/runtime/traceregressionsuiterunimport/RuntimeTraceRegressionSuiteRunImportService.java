package com.uniovi.rag.application.service.runtime.traceregressionsuiterunimport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunPersistenceService;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteResult;
import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunSourceType;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunDetailDto;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import com.uniovi.rag.infrastructure.zip.ZipIoGuards;

import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * P44: ZIP import adapter (non-owning) — validates a P43-shaped ZIP, maps {@link RuntimeTraceRegressionSuiteRunDetailDto} to
 * {@link RuntimeTraceRegressionSuiteResult} via {@link RuntimeTraceRegressionSuiteRunDetailDto#toRuntimeTraceRegressionSuiteResultForImport()},
 * then delegates {@link RuntimeTraceRegressionSuiteRunPersistenceService#createRun} only.
 */
@Service
public class RuntimeTraceRegressionSuiteRunImportService {

    public static final long MAX_IMPORT_ZIP_BYTES = 2097152L;

    private final RuntimeTraceRegressionSuiteRunPersistenceService runPersistenceService;
    private final ObjectMapper objectMapper;

    public RuntimeTraceRegressionSuiteRunImportService(RuntimeTraceRegressionSuiteRunPersistenceService runPersistenceService) {
        this.runPersistenceService = runPersistenceService;
        this.objectMapper =
                new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .disable(SerializationFeature.INDENT_OUTPUT)
                        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    public UUID importRunZip(byte[] body, UUID userId) {
        if (body == null || body.length == 0 || body.length > MAX_IMPORT_ZIP_BYTES) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid zip");
        }
        byte[][] parts = readManifestAndRunBytes(body);
        byte[] manifestBytes = parts[0];
        byte[] runJsonBytes = parts[1];

        JsonNode manifestRoot;
        try {
            manifestRoot = objectMapper.readTree(manifestBytes);
        } catch (IOException ex) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid manifest", ex);
        }
        validateManifest(manifestRoot, body.length);

        RuntimeTraceRegressionSuiteRunDetailDto detail;
        try {
            detail = objectMapper.readValue(runJsonBytes, RuntimeTraceRegressionSuiteRunDetailDto.class);
        } catch (IOException ex) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid run.json", ex);
        }

        assertManifestMatchesDetail(manifestRoot, detail);
        assertSourceTypeDefinitionIdPairing(detail);

        RuntimeTraceRegressionSuiteResult result = detail.toRuntimeTraceRegressionSuiteResultForImport();
        RuntimeTraceRegressionSuiteRunSourceType sourceType =
                RuntimeTraceRegressionSuiteRunSourceType.valueOf(detail.sourceType());
        Optional<UUID> definitionId = Optional.ofNullable(detail.definitionId());
        return runPersistenceService.createRun(userId, sourceType, definitionId, result);
    }

    /**
     * P54: validates a P53-shaped ZIP (same layout as {@link #importRunZip}), persists via
     * {@link RuntimeTraceRegressionSuiteRunPersistenceService#createRun} with {@link RuntimeTraceRegressionSuiteRunSourceType#SAVED_DEFINITION}
     * and {@link Optional#of} the path {@code definitionId} only.
     */
    public UUID importRunZipForDefinition(byte[] body, UUID userId, UUID definitionId) {
        if (body == null || body.length == 0 || body.length > MAX_IMPORT_ZIP_BYTES) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid zip");
        }
        byte[][] parts = readManifestAndRunBytes(body);
        byte[] manifestBytes = parts[0];
        byte[] runJsonBytes = parts[1];

        JsonNode manifestRoot;
        try {
            manifestRoot = objectMapper.readTree(manifestBytes);
        } catch (IOException ex) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid manifest", ex);
        }
        validateManifestDefinitionScoped(manifestRoot, body.length, definitionId);

        RuntimeTraceRegressionSuiteRunDetailDto detail;
        try {
            detail = objectMapper.readValue(runJsonBytes, RuntimeTraceRegressionSuiteRunDetailDto.class);
        } catch (IOException ex) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid run.json", ex);
        }

        assertManifestMatchesDetail(manifestRoot, detail);
        assertSourceTypeDefinitionIdPairing(detail);
        if (detail.definitionId() == null || !detail.definitionId().equals(definitionId)) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("artifact manifest and run.json mismatch");
        }

        RuntimeTraceRegressionSuiteResult result = detail.toRuntimeTraceRegressionSuiteResultForImport();
        return runPersistenceService.createRun(
                userId, RuntimeTraceRegressionSuiteRunSourceType.SAVED_DEFINITION, Optional.of(definitionId), result);
    }

    private void validateManifestDefinitionScoped(JsonNode root, int bodyLength, UUID pathDefinitionId) {
        if (!root.hasNonNull("exportKind")
                || !root.get("exportKind").isTextual()
                || !"REGRESSION_SUITE_RUN".equals(root.get("exportKind").asText())) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid manifest");
        }
        if (!root.has("schemaVersion")
                || !root.get("schemaVersion").isIntegralNumber()
                || root.get("schemaVersion").intValue() != 1) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid manifest");
        }
        if (!root.has("truncated")
                || !root.get("truncated").isBoolean()
                || root.get("truncated").booleanValue()) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid manifest");
        }
        if (!root.has("zipSizeBytes")
                || !root.get("zipSizeBytes").isIntegralNumber()
                || root.get("zipSizeBytes").longValue() != (long) bodyLength) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid manifest");
        }
        if (!root.hasNonNull("selectorType")
                || !root.get("selectorType").isTextual()
                || !"SAVED_DEFINITION_SCOPED_RUN".equals(root.get("selectorType").asText())) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid manifest");
        }
        if (!root.has("scope") || !root.get("scope").isObject()) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid manifest");
        }
        JsonNode scope = root.get("scope");
        if (!scope.hasNonNull("runId") || !scope.get("runId").isTextual()) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid manifest");
        }
        try {
            UUID.fromString(scope.get("runId").asText());
        } catch (IllegalArgumentException ex) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid manifest");
        }
        if (!scope.hasNonNull("definitionId") || !scope.get("definitionId").isTextual()) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid manifest");
        }
        try {
            UUID.fromString(scope.get("definitionId").asText());
        } catch (IllegalArgumentException ex) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid manifest");
        }
        if (!scope.get("definitionId").asText().equals(pathDefinitionId.toString())) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid manifest");
        }
        if (!root.hasNonNull("runId") || !root.get("runId").isTextual()) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid manifest");
        }
        try {
            UUID.fromString(root.get("runId").asText());
        } catch (IllegalArgumentException ex) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid manifest");
        }
        if (!scope.get("runId").asText().equals(root.get("runId").asText())) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid manifest");
        }
        if (!root.hasNonNull("definitionId") || !root.get("definitionId").isTextual()) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid manifest");
        }
        try {
            UUID.fromString(root.get("definitionId").asText());
        } catch (IllegalArgumentException ex) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid manifest");
        }
        if (!root.get("definitionId").asText().equals(scope.get("definitionId").asText())) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid manifest");
        }
        if (!root.hasNonNull("sourceType") || !root.get("sourceType").isTextual()) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid manifest");
        }
        if (!root.hasNonNull("suiteOutcome") || !root.get("suiteOutcome").isTextual()) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid manifest");
        }
        if (!manifestHasIntegralCount(root, "requestedEntryCount")
                || !manifestHasIntegralCount(root, "processedEntryCount")
                || !manifestHasIntegralCount(root, "batchReturnedCount")
                || !manifestHasIntegralCount(root, "executionFailedCount")
                || !manifestHasIntegralCount(root, "batchNotAttemptedSubcount")) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid manifest");
        }
    }

    private void validateManifest(JsonNode root, int bodyLength) {
        if (!root.hasNonNull("exportKind")
                || !root.get("exportKind").isTextual()
                || !"REGRESSION_SUITE_RUN".equals(root.get("exportKind").asText())) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid manifest");
        }
        if (!root.has("schemaVersion")
                || !root.get("schemaVersion").isIntegralNumber()
                || root.get("schemaVersion").intValue() != 1) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid manifest");
        }
        if (!root.has("truncated")
                || !root.get("truncated").isBoolean()
                || root.get("truncated").booleanValue()) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid manifest");
        }
        if (!root.has("zipSizeBytes")
                || !root.get("zipSizeBytes").isIntegralNumber()
                || root.get("zipSizeBytes").longValue() != (long) bodyLength) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid manifest");
        }
        if (!root.hasNonNull("selectorType")
                || !root.get("selectorType").isTextual()
                || !"SAVED_RUN_BY_ID".equals(root.get("selectorType").asText())) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid manifest");
        }
        if (!root.has("scope") || !root.get("scope").isObject()) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid manifest");
        }
        JsonNode scope = root.get("scope");
        if (!scope.hasNonNull("runId") || !scope.get("runId").isTextual()) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid manifest");
        }
        try {
            UUID.fromString(scope.get("runId").asText());
        } catch (IllegalArgumentException ex) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid manifest");
        }
        if (!root.hasNonNull("runId") || !root.get("runId").isTextual()) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid manifest");
        }
        if (!scope.get("runId").asText().equals(root.get("runId").asText())) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid manifest");
        }
        if (!root.hasNonNull("sourceType") || !root.get("sourceType").isTextual()) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid manifest");
        }
        if (!root.has("definitionId")) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid manifest");
        }
        JsonNode defId = root.get("definitionId");
        if (!defId.isNull() && !defId.isTextual()) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid manifest");
        }
        if (defId.isTextual()) {
            try {
                UUID.fromString(defId.asText());
            } catch (IllegalArgumentException ex) {
                throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid manifest");
            }
        }
        if (!root.hasNonNull("suiteOutcome") || !root.get("suiteOutcome").isTextual()) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid manifest");
        }
        if (!manifestHasIntegralCount(root, "requestedEntryCount")
                || !manifestHasIntegralCount(root, "processedEntryCount")
                || !manifestHasIntegralCount(root, "batchReturnedCount")
                || !manifestHasIntegralCount(root, "executionFailedCount")
                || !manifestHasIntegralCount(root, "batchNotAttemptedSubcount")) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid manifest");
        }
    }

    private static boolean manifestHasIntegralCount(JsonNode root, String field) {
        return root.has(field) && root.get(field).isIntegralNumber();
    }

    private void assertManifestMatchesDetail(JsonNode m, RuntimeTraceRegressionSuiteRunDetailDto detail) {
        if (!m.get("runId").asText().equals(detail.id().toString())) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("artifact manifest and run.json mismatch");
        }
        if (!m.get("sourceType").asText().equals(detail.sourceType())) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("artifact manifest and run.json mismatch");
        }
        JsonNode defNode = m.get("definitionId");
        if (defNode.isNull()) {
            if (detail.definitionId() != null) {
                throw new RuntimeTraceRegressionSuiteRunImportRejectedException("artifact manifest and run.json mismatch");
            }
        } else {
            if (detail.definitionId() == null
                    || !detail.definitionId().toString().equals(defNode.asText())) {
                throw new RuntimeTraceRegressionSuiteRunImportRejectedException("artifact manifest and run.json mismatch");
            }
        }
        if (!m.get("suiteOutcome").asText().equals(detail.suiteOutcome())) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("artifact manifest and run.json mismatch");
        }
        if (m.get("requestedEntryCount").intValue() != detail.requestedEntryCount()
                || m.get("processedEntryCount").intValue() != detail.processedEntryCount()
                || m.get("batchReturnedCount").intValue() != detail.batchReturnedCount()
                || m.get("executionFailedCount").intValue() != detail.executionFailedCount()
                || m.get("batchNotAttemptedSubcount").intValue() != detail.batchNotAttemptedSubcount()) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("artifact manifest and run.json mismatch");
        }
    }

    private static void assertSourceTypeDefinitionIdPairing(RuntimeTraceRegressionSuiteRunDetailDto detail) {
        RuntimeTraceRegressionSuiteRunSourceType st =
                RuntimeTraceRegressionSuiteRunSourceType.valueOf(detail.sourceType());
        if (st == RuntimeTraceRegressionSuiteRunSourceType.SAVED_DEFINITION && detail.definitionId() == null) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid sourceType definitionId pairing");
        }
        if (st == RuntimeTraceRegressionSuiteRunSourceType.AD_HOC && detail.definitionId() != null) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid sourceType definitionId pairing");
        }
    }

    private static byte[][] readManifestAndRunBytes(byte[] body) {
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(body))) {
            ZipEntry e1 = zin.getNextEntry();
            if (e1 == null || e1.isDirectory() || entryNameIsDirectory(e1.getName())) {
                throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid zip");
            }
            try {
                ZipIoGuards.requireSafeEntryName(e1.getName());
            } catch (IOException ex) {
                throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid zip", ex);
            }
            if (!"manifest.json".equals(e1.getName()) || e1.getMethod() != ZipEntry.STORED) {
                throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid zip");
            }
            byte[] manifestBytes = ZipIoGuards.readStoredEntryBytes(zin, e1, MAX_IMPORT_ZIP_BYTES);
            zin.closeEntry();

            ZipEntry e2 = zin.getNextEntry();
            if (e2 == null || e2.isDirectory() || entryNameIsDirectory(e2.getName())) {
                throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid zip");
            }
            try {
                ZipIoGuards.requireSafeEntryName(e2.getName());
            } catch (IOException ex) {
                throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid zip", ex);
            }
            if (!"run.json".equals(e2.getName()) || e2.getMethod() != ZipEntry.STORED) {
                throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid zip");
            }
            byte[] runBytes = ZipIoGuards.readStoredEntryBytes(zin, e2, MAX_IMPORT_ZIP_BYTES);
            zin.closeEntry();

            ZipEntry e3 = zin.getNextEntry();
            if (e3 != null) {
                throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid zip");
            }
            return new byte[][] {manifestBytes, runBytes};
        } catch (IOException ex) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid zip", ex);
        }
    }

    private static boolean entryNameIsDirectory(String name) {
        return name != null && name.endsWith("/");
    }
}

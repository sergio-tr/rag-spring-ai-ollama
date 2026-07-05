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
import com.uniovi.rag.infrastructure.zip.ZipExpansionBudget;
import com.uniovi.rag.infrastructure.zip.ZipIoGuards;

import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * P44: ZIP import adapter (non-owning) - validates a P43-shaped ZIP, maps {@link RuntimeTraceRegressionSuiteRunDetailDto} to
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
        RuntimeTraceRegressionSuiteRunImportRejectedException invalidManifest =
                new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid manifest");

        RuntimeTraceRegressionSuiteRunImportManifestValidators.requireExportKindRegressionSuiteRun(root, invalidManifest);
        RuntimeTraceRegressionSuiteRunImportManifestValidators.requireSchemaVersion1(root, invalidManifest);
        RuntimeTraceRegressionSuiteRunImportManifestValidators.requireTruncatedFalse(root, invalidManifest);
        RuntimeTraceRegressionSuiteRunImportManifestValidators.requireZipSizeBytes(root, bodyLength, invalidManifest);
        RuntimeTraceRegressionSuiteRunImportManifestValidators.requireSelectorType(
                root, "SAVED_DEFINITION_SCOPED_RUN", invalidManifest);

        JsonNode scope =
                RuntimeTraceRegressionSuiteRunImportManifestValidators.requireObject(root, "scope", invalidManifest);
        UUID scopeRunId =
                RuntimeTraceRegressionSuiteRunImportManifestValidators.requireUuidTextInObject(
                        scope, RuntimeTraceRegressionSuiteRunImportManifestValidators.FIELD_RUN_ID, invalidManifest);
        UUID scopeDefinitionId =
                RuntimeTraceRegressionSuiteRunImportManifestValidators.requireUuidTextInObject(
                        scope, RuntimeTraceRegressionSuiteRunImportManifestValidators.FIELD_DEFINITION_ID, invalidManifest);
        if (!scopeDefinitionId.toString().equals(pathDefinitionId.toString())) {
            throw invalidManifest;
        }

        UUID rootRunId =
                RuntimeTraceRegressionSuiteRunImportManifestValidators.requireUuidText(
                        root, RuntimeTraceRegressionSuiteRunImportManifestValidators.FIELD_RUN_ID, invalidManifest);
        if (!scopeRunId.equals(rootRunId)) {
            throw invalidManifest;
        }
        UUID rootDefinitionId =
                RuntimeTraceRegressionSuiteRunImportManifestValidators.requireUuidText(
                        root, RuntimeTraceRegressionSuiteRunImportManifestValidators.FIELD_DEFINITION_ID, invalidManifest);
        if (!scopeDefinitionId.equals(rootDefinitionId)) {
            throw invalidManifest;
        }

        RuntimeTraceRegressionSuiteRunImportManifestValidators.requireText(
                root, RuntimeTraceRegressionSuiteRunImportManifestValidators.FIELD_SOURCE_TYPE, invalidManifest);
        RuntimeTraceRegressionSuiteRunImportManifestValidators.requireText(
                root, RuntimeTraceRegressionSuiteRunImportManifestValidators.FIELD_SUITE_OUTCOME, invalidManifest);
        requireCounts(root, invalidManifest);
    }

    private void validateManifest(JsonNode root, int bodyLength) {
        RuntimeTraceRegressionSuiteRunImportRejectedException invalidManifest =
                new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid manifest");

        RuntimeTraceRegressionSuiteRunImportManifestValidators.requireExportKindRegressionSuiteRun(root, invalidManifest);
        RuntimeTraceRegressionSuiteRunImportManifestValidators.requireSchemaVersion1(root, invalidManifest);
        RuntimeTraceRegressionSuiteRunImportManifestValidators.requireTruncatedFalse(root, invalidManifest);
        RuntimeTraceRegressionSuiteRunImportManifestValidators.requireZipSizeBytes(root, bodyLength, invalidManifest);
        RuntimeTraceRegressionSuiteRunImportManifestValidators.requireSelectorType(root, "SAVED_RUN_BY_ID", invalidManifest);

        JsonNode scope =
                RuntimeTraceRegressionSuiteRunImportManifestValidators.requireObject(root, "scope", invalidManifest);
        UUID scopeRunId =
                RuntimeTraceRegressionSuiteRunImportManifestValidators.requireUuidTextInObject(
                        scope, RuntimeTraceRegressionSuiteRunImportManifestValidators.FIELD_RUN_ID, invalidManifest);
        UUID rootRunId =
                RuntimeTraceRegressionSuiteRunImportManifestValidators.requireUuidText(
                        root, RuntimeTraceRegressionSuiteRunImportManifestValidators.FIELD_RUN_ID, invalidManifest);
        if (!scopeRunId.equals(rootRunId)) {
            throw invalidManifest;
        }

        RuntimeTraceRegressionSuiteRunImportManifestValidators.requireText(
                root, RuntimeTraceRegressionSuiteRunImportManifestValidators.FIELD_SOURCE_TYPE, invalidManifest);
        RuntimeTraceRegressionSuiteRunImportManifestValidators.optionalUuidTextOrNull(
                root, RuntimeTraceRegressionSuiteRunImportManifestValidators.FIELD_DEFINITION_ID, invalidManifest);
        RuntimeTraceRegressionSuiteRunImportManifestValidators.requireText(
                root, RuntimeTraceRegressionSuiteRunImportManifestValidators.FIELD_SUITE_OUTCOME, invalidManifest);
        requireCounts(root, invalidManifest);
    }

    private static void requireCounts(JsonNode root, RuntimeException invalidManifest) {
        if (!RuntimeTraceRegressionSuiteRunImportManifestValidators.hasIntegralCount(root, "requestedEntryCount")
                || !RuntimeTraceRegressionSuiteRunImportManifestValidators.hasIntegralCount(root, "processedEntryCount")
                || !RuntimeTraceRegressionSuiteRunImportManifestValidators.hasIntegralCount(root, "batchReturnedCount")
                || !RuntimeTraceRegressionSuiteRunImportManifestValidators.hasIntegralCount(root, "executionFailedCount")
                || !RuntimeTraceRegressionSuiteRunImportManifestValidators.hasIntegralCount(root, "batchNotAttemptedSubcount")) {
            throw invalidManifest;
        }
    }

    private void assertManifestMatchesDetail(JsonNode m, RuntimeTraceRegressionSuiteRunDetailDto detail) {
        if (!m.get(RuntimeTraceRegressionSuiteRunImportManifestValidators.FIELD_RUN_ID)
                .asText()
                .equals(detail.id().toString())) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("artifact manifest and run.json mismatch");
        }
        if (!m.get(RuntimeTraceRegressionSuiteRunImportManifestValidators.FIELD_SOURCE_TYPE)
                .asText()
                .equals(detail.sourceType())) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("artifact manifest and run.json mismatch");
        }
        JsonNode defNode = m.get(RuntimeTraceRegressionSuiteRunImportManifestValidators.FIELD_DEFINITION_ID);
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
        if (!m.get(RuntimeTraceRegressionSuiteRunImportManifestValidators.FIELD_SUITE_OUTCOME)
                .asText()
                .equals(detail.suiteOutcome())) {
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
            ZipExpansionBudget budget = ZipExpansionBudget.forUploadedZip(MAX_IMPORT_ZIP_BYTES);
            ZipEntry e1 = zin.getNextEntry();
            if (e1 == null || e1.isDirectory() || entryNameIsDirectory(e1.getName())) {
                throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid zip");
            }
            requireSafeZipEntryName(e1.getName());
            if (!"manifest.json".equals(e1.getName()) || e1.getMethod() != ZipEntry.STORED) {
                throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid zip");
            }
            byte[] manifestBytes = ZipIoGuards.readStoredEntryBytes(zin, e1, MAX_IMPORT_ZIP_BYTES, budget);
            zin.closeEntry();

            ZipEntry e2 = zin.getNextEntry();
            if (e2 == null || e2.isDirectory() || entryNameIsDirectory(e2.getName())) {
                throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid zip");
            }
            requireSafeZipEntryName(e2.getName());
            if (!"run.json".equals(e2.getName()) || e2.getMethod() != ZipEntry.STORED) {
                throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid zip");
            }
            byte[] runBytes = ZipIoGuards.readStoredEntryBytes(zin, e2, MAX_IMPORT_ZIP_BYTES, budget);
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

    private static void requireSafeZipEntryName(String entryName) {
        try {
            ZipIoGuards.requireSafeEntryName(entryName);
        } catch (IOException ex) {
            throw new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid zip", ex);
        }
    }
}

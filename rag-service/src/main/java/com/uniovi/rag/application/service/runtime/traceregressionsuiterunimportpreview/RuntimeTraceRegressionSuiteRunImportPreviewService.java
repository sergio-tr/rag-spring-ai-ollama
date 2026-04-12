package com.uniovi.rag.application.service.runtime.traceregressionsuiterunimportpreview;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunSourceType;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunDetailDto;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuiterunimportpreview.RuntimeTraceRegressionSuiteRunImportPreviewResponseDto;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * P45: ZIP run import preview adapter (non-owning) — validates a P43-shaped ZIP and deserializes {@code run.json} to
 * {@link RuntimeTraceRegressionSuiteRunDetailDto} without persistence, storage, or delegating to the P44 import workflow.
 */
@Service
public class RuntimeTraceRegressionSuiteRunImportPreviewService {

    public static final long MAX_PREVIEW_ZIP_BYTES = 2097152L;

    private static final Set<String> MANIFEST_SOURCE_TYPES = Set.of("AD_HOC", "SAVED_DEFINITION");

    private static final Set<String> MANIFEST_SUITE_OUTCOMES =
            Set.of(
                    "NOT_ATTEMPTED",
                    "EMPTY_SUITE",
                    "COMPLETED_ALL_BATCH_RETURNS",
                    "COMPLETED_WITH_ENTRY_EXECUTION_FAILURES");

    private final ObjectMapper objectMapper;

    public RuntimeTraceRegressionSuiteRunImportPreviewService() {
        this.objectMapper =
                new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .disable(SerializationFeature.INDENT_OUTPUT)
                        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    /**
     * Parses a P43-strict run ZIP and returns a preview DTO (no persistence).
     */
    public RuntimeTraceRegressionSuiteRunImportPreviewResponseDto previewImportZip(byte[] body) {
        byte[][] parts = readManifestAndRunBytes(body);
        byte[] manifestBytes = parts[0];
        byte[] runJsonBytes = parts[1];

        JsonNode manifestRoot;
        try {
            manifestRoot = objectMapper.readTree(manifestBytes);
        } catch (JsonProcessingException ex) {
            throw new RuntimeTraceRegressionSuiteRunImportPreviewRejectedException("invalid manifest", ex);
        } catch (IOException ex) {
            throw new RuntimeTraceRegressionSuiteRunImportPreviewRejectedException("invalid manifest", ex);
        }
        validateManifest(manifestRoot, body.length);

        RuntimeTraceRegressionSuiteRunDetailDto runDto;
        try {
            runDto = objectMapper.readValue(runJsonBytes, RuntimeTraceRegressionSuiteRunDetailDto.class);
        } catch (JsonProcessingException ex) {
            throw new RuntimeTraceRegressionSuiteRunImportPreviewRejectedException("invalid run.json", ex);
        } catch (IOException ex) {
            throw new RuntimeTraceRegressionSuiteRunImportPreviewRejectedException("invalid run.json", ex);
        }

        assertManifestMatchesRunDto(manifestRoot, runDto);
        assertSourceTypeDefinitionIdPairing(runDto);

        return new RuntimeTraceRegressionSuiteRunImportPreviewResponseDto(runDto, true, List.of());
    }

    private void validateManifest(JsonNode root, int bodyLength) {
        if (!root.hasNonNull("exportKind")
                || !root.get("exportKind").isTextual()
                || !"REGRESSION_SUITE_RUN".equals(root.get("exportKind").asText())) {
            throw new RuntimeTraceRegressionSuiteRunImportPreviewRejectedException("invalid manifest");
        }
        if (!root.has("schemaVersion")
                || !root.get("schemaVersion").isIntegralNumber()
                || root.get("schemaVersion").intValue() != 1) {
            throw new RuntimeTraceRegressionSuiteRunImportPreviewRejectedException("invalid manifest");
        }
        if (!root.has("truncated")
                || !root.get("truncated").isBoolean()
                || root.get("truncated").booleanValue()) {
            throw new RuntimeTraceRegressionSuiteRunImportPreviewRejectedException("invalid manifest");
        }
        if (!root.has("zipSizeBytes")
                || !root.get("zipSizeBytes").isIntegralNumber()
                || root.get("zipSizeBytes").longValue() != (long) bodyLength) {
            throw new RuntimeTraceRegressionSuiteRunImportPreviewRejectedException("invalid manifest");
        }
        if (!root.hasNonNull("selectorType")
                || !root.get("selectorType").isTextual()
                || !"SAVED_RUN_BY_ID".equals(root.get("selectorType").asText())) {
            throw new RuntimeTraceRegressionSuiteRunImportPreviewRejectedException("invalid manifest");
        }
        if (!root.has("scope") || !root.get("scope").isObject()) {
            throw new RuntimeTraceRegressionSuiteRunImportPreviewRejectedException("invalid manifest");
        }
        JsonNode scope = root.get("scope");
        if (!scope.hasNonNull("runId") || !scope.get("runId").isTextual()) {
            throw new RuntimeTraceRegressionSuiteRunImportPreviewRejectedException("invalid manifest");
        }
        try {
            UUID.fromString(scope.get("runId").asText());
        } catch (IllegalArgumentException ex) {
            throw new RuntimeTraceRegressionSuiteRunImportPreviewRejectedException("invalid manifest");
        }
        if (!root.hasNonNull("runId") || !root.get("runId").isTextual()) {
            throw new RuntimeTraceRegressionSuiteRunImportPreviewRejectedException("invalid manifest");
        }
        try {
            UUID.fromString(root.get("runId").asText());
        } catch (IllegalArgumentException ex) {
            throw new RuntimeTraceRegressionSuiteRunImportPreviewRejectedException("invalid manifest");
        }
        if (!scope.get("runId").asText().equals(root.get("runId").asText())) {
            throw new RuntimeTraceRegressionSuiteRunImportPreviewRejectedException("invalid manifest");
        }
        if (!root.hasNonNull("sourceType") || !root.get("sourceType").isTextual()) {
            throw new RuntimeTraceRegressionSuiteRunImportPreviewRejectedException("invalid manifest");
        }
        String sourceTypeText = root.get("sourceType").asText();
        if (!MANIFEST_SOURCE_TYPES.contains(sourceTypeText)) {
            throw new RuntimeTraceRegressionSuiteRunImportPreviewRejectedException("invalid manifest");
        }
        if (!root.has("definitionId")) {
            throw new RuntimeTraceRegressionSuiteRunImportPreviewRejectedException("invalid manifest");
        }
        JsonNode defId = root.get("definitionId");
        if (!defId.isNull() && !defId.isTextual()) {
            throw new RuntimeTraceRegressionSuiteRunImportPreviewRejectedException("invalid manifest");
        }
        if (defId.isTextual()) {
            try {
                UUID.fromString(defId.asText());
            } catch (IllegalArgumentException ex) {
                throw new RuntimeTraceRegressionSuiteRunImportPreviewRejectedException("invalid manifest");
            }
        }
        if (!root.hasNonNull("suiteOutcome") || !root.get("suiteOutcome").isTextual()) {
            throw new RuntimeTraceRegressionSuiteRunImportPreviewRejectedException("invalid manifest");
        }
        String suiteOutcomeText = root.get("suiteOutcome").asText();
        if (!MANIFEST_SUITE_OUTCOMES.contains(suiteOutcomeText)) {
            throw new RuntimeTraceRegressionSuiteRunImportPreviewRejectedException("invalid manifest");
        }
        if (!manifestHasIntegralCount(root, "requestedEntryCount")
                || !manifestHasIntegralCount(root, "processedEntryCount")
                || !manifestHasIntegralCount(root, "batchReturnedCount")
                || !manifestHasIntegralCount(root, "executionFailedCount")
                || !manifestHasIntegralCount(root, "batchNotAttemptedSubcount")) {
            throw new RuntimeTraceRegressionSuiteRunImportPreviewRejectedException("invalid manifest");
        }
    }

    private static boolean manifestHasIntegralCount(JsonNode root, String field) {
        return root.has(field) && root.get(field).isIntegralNumber();
    }

    private static void assertManifestMatchesRunDto(JsonNode m, RuntimeTraceRegressionSuiteRunDetailDto detail) {
        if (!m.get("runId").asText().equals(detail.id().toString())) {
            throw new RuntimeTraceRegressionSuiteRunImportPreviewRejectedException("artifact manifest and run.json mismatch");
        }
        if (!m.get("sourceType").asText().equals(detail.sourceType())) {
            throw new RuntimeTraceRegressionSuiteRunImportPreviewRejectedException("artifact manifest and run.json mismatch");
        }
        JsonNode defNode = m.get("definitionId");
        if (defNode.isNull()) {
            if (detail.definitionId() != null) {
                throw new RuntimeTraceRegressionSuiteRunImportPreviewRejectedException("artifact manifest and run.json mismatch");
            }
        } else {
            if (detail.definitionId() == null
                    || !detail.definitionId().toString().equals(defNode.asText())) {
                throw new RuntimeTraceRegressionSuiteRunImportPreviewRejectedException("artifact manifest and run.json mismatch");
            }
        }
        if (!m.get("suiteOutcome").asText().equals(detail.suiteOutcome())) {
            throw new RuntimeTraceRegressionSuiteRunImportPreviewRejectedException("artifact manifest and run.json mismatch");
        }
        if (m.get("requestedEntryCount").intValue() != detail.requestedEntryCount()
                || m.get("processedEntryCount").intValue() != detail.processedEntryCount()
                || m.get("batchReturnedCount").intValue() != detail.batchReturnedCount()
                || m.get("executionFailedCount").intValue() != detail.executionFailedCount()
                || m.get("batchNotAttemptedSubcount").intValue() != detail.batchNotAttemptedSubcount()) {
            throw new RuntimeTraceRegressionSuiteRunImportPreviewRejectedException("artifact manifest and run.json mismatch");
        }
    }

    private static void assertSourceTypeDefinitionIdPairing(RuntimeTraceRegressionSuiteRunDetailDto detail) {
        RuntimeTraceRegressionSuiteRunSourceType st =
                RuntimeTraceRegressionSuiteRunSourceType.valueOf(detail.sourceType());
        if (st == RuntimeTraceRegressionSuiteRunSourceType.SAVED_DEFINITION && detail.definitionId() == null) {
            throw new RuntimeTraceRegressionSuiteRunImportPreviewRejectedException("invalid sourceType definitionId pairing");
        }
        if (st == RuntimeTraceRegressionSuiteRunSourceType.AD_HOC && detail.definitionId() != null) {
            throw new RuntimeTraceRegressionSuiteRunImportPreviewRejectedException("invalid sourceType definitionId pairing");
        }
    }

    private static byte[][] readManifestAndRunBytes(byte[] body) {
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(body))) {
            ZipEntry e1 = zin.getNextEntry();
            if (e1 == null || e1.isDirectory() || entryNameIsDirectory(e1.getName())) {
                throw new RuntimeTraceRegressionSuiteRunImportPreviewRejectedException("invalid zip");
            }
            if (!"manifest.json".equals(e1.getName()) || e1.getMethod() != ZipEntry.STORED) {
                throw new RuntimeTraceRegressionSuiteRunImportPreviewRejectedException("invalid zip");
            }
            byte[] manifestBytes = readStoredEntryBytes(zin, e1);
            zin.closeEntry();

            ZipEntry e2 = zin.getNextEntry();
            if (e2 == null || e2.isDirectory() || entryNameIsDirectory(e2.getName())) {
                throw new RuntimeTraceRegressionSuiteRunImportPreviewRejectedException("invalid zip");
            }
            if (!"run.json".equals(e2.getName()) || e2.getMethod() != ZipEntry.STORED) {
                throw new RuntimeTraceRegressionSuiteRunImportPreviewRejectedException("invalid zip");
            }
            byte[] runBytes = readStoredEntryBytes(zin, e2);
            zin.closeEntry();

            ZipEntry e3 = zin.getNextEntry();
            if (e3 != null) {
                throw new RuntimeTraceRegressionSuiteRunImportPreviewRejectedException("invalid zip");
            }
            return new byte[][] {manifestBytes, runBytes};
        } catch (IOException ex) {
            throw new RuntimeTraceRegressionSuiteRunImportPreviewRejectedException("invalid zip", ex);
        }
    }

    private static boolean entryNameIsDirectory(String name) {
        return name != null && name.endsWith("/");
    }

    private static byte[] readStoredEntryBytes(ZipInputStream zin, ZipEntry entry) throws IOException {
        long size = entry.getSize();
        if (size < 0 || size > Integer.MAX_VALUE) {
            throw new IOException("invalid entry size");
        }
        return zin.readNBytes((int) size);
    }
}

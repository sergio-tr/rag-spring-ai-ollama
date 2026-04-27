package com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionimportpreview;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionDetailDto;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinitionimportpreview.RuntimeTraceRegressionSuiteDefinitionImportPreviewResponseDto;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import com.uniovi.rag.infrastructure.zip.ZipIoGuards;

import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * P40: ZIP import preview adapter (non-owning) — validates a P38-strict ZIP and deserializes {@code definition.json} to
 * {@link RuntimeTraceRegressionSuiteDefinitionDetailDto} without persisting or delegating to definition or import services.
 */
@Service
public class RuntimeTraceRegressionSuiteDefinitionImportPreviewService {

    public static final long MAX_PREVIEW_ZIP_BYTES = 2097152L;

    private final ObjectMapper objectMapper;

    public RuntimeTraceRegressionSuiteDefinitionImportPreviewService() {
        this.objectMapper =
                new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .disable(SerializationFeature.INDENT_OUTPUT)
                        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    /**
     * Parses a P38-strict definition ZIP and returns a preview DTO (no persistence).
     */
    public RuntimeTraceRegressionSuiteDefinitionImportPreviewResponseDto previewImportZip(byte[] zipBytes) {
        if (zipBytes == null || zipBytes.length == 0 || zipBytes.length > MAX_PREVIEW_ZIP_BYTES) {
            throw new RuntimeTraceRegressionSuiteDefinitionImportPreviewRejectedException("invalid zip");
        }
        byte[][] parts = readManifestAndDefinitionBytes(zipBytes);
        byte[] manifestBytes = parts[0];
        byte[] definitionBytes = parts[1];

        JsonNode manifestRoot;
        try {
            manifestRoot = objectMapper.readTree(manifestBytes);
        } catch (IOException ex) {
            throw new RuntimeTraceRegressionSuiteDefinitionImportPreviewRejectedException("invalid manifest", ex);
        }
        validateManifest(manifestRoot, zipBytes.length);

        RuntimeTraceRegressionSuiteDefinitionDetailDto detail;
        try {
            detail = objectMapper.readValue(definitionBytes, RuntimeTraceRegressionSuiteDefinitionDetailDto.class);
        } catch (IOException ex) {
            throw new RuntimeTraceRegressionSuiteDefinitionImportPreviewRejectedException("invalid definition.json", ex);
        }

        return new RuntimeTraceRegressionSuiteDefinitionImportPreviewResponseDto(detail, true, List.of());
    }

    private void validateManifest(JsonNode root, int bodyLength) {
        if (!root.hasNonNull("exportKind")
                || !root.get("exportKind").isTextual()
                || !"REGRESSION_SUITE_DEFINITION".equals(root.get("exportKind").asText())) {
            throw new RuntimeTraceRegressionSuiteDefinitionImportPreviewRejectedException("invalid manifest");
        }
        if (!root.has("schemaVersion")
                || !root.get("schemaVersion").isIntegralNumber()
                || root.get("schemaVersion").intValue() != 1) {
            throw new RuntimeTraceRegressionSuiteDefinitionImportPreviewRejectedException("invalid manifest");
        }
        if (!root.has("truncated")
                || !root.get("truncated").isBoolean()
                || root.get("truncated").booleanValue()) {
            throw new RuntimeTraceRegressionSuiteDefinitionImportPreviewRejectedException("invalid manifest");
        }
        if (!root.has("zipSizeBytes")
                || !root.get("zipSizeBytes").isIntegralNumber()
                || root.get("zipSizeBytes").longValue() != (long) bodyLength) {
            throw new RuntimeTraceRegressionSuiteDefinitionImportPreviewRejectedException("invalid manifest");
        }
    }

    private static byte[][] readManifestAndDefinitionBytes(byte[] body) {
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(body))) {
            ZipEntry e1 = zin.getNextEntry();
            if (e1 == null || e1.isDirectory() || entryNameIsDirectory(e1.getName())) {
                throw new RuntimeTraceRegressionSuiteDefinitionImportPreviewRejectedException("invalid zip");
            }
            try {
                ZipIoGuards.requireSafeEntryName(e1.getName());
            } catch (IOException ex) {
                throw new RuntimeTraceRegressionSuiteDefinitionImportPreviewRejectedException("invalid zip", ex);
            }
            if (!"manifest.json".equals(e1.getName()) || e1.getMethod() != ZipEntry.STORED) {
                throw new RuntimeTraceRegressionSuiteDefinitionImportPreviewRejectedException("invalid zip");
            }
            byte[] manifestBytes = ZipIoGuards.readStoredEntryBytes(zin, e1, MAX_PREVIEW_ZIP_BYTES);
            zin.closeEntry();

            ZipEntry e2 = zin.getNextEntry();
            if (e2 == null || e2.isDirectory() || entryNameIsDirectory(e2.getName())) {
                throw new RuntimeTraceRegressionSuiteDefinitionImportPreviewRejectedException("invalid zip");
            }
            try {
                ZipIoGuards.requireSafeEntryName(e2.getName());
            } catch (IOException ex) {
                throw new RuntimeTraceRegressionSuiteDefinitionImportPreviewRejectedException("invalid zip", ex);
            }
            if (!"definition.json".equals(e2.getName()) || e2.getMethod() != ZipEntry.STORED) {
                throw new RuntimeTraceRegressionSuiteDefinitionImportPreviewRejectedException("invalid zip");
            }
            byte[] definitionBytes = ZipIoGuards.readStoredEntryBytes(zin, e2, MAX_PREVIEW_ZIP_BYTES);
            zin.closeEntry();

            ZipEntry e3 = zin.getNextEntry();
            if (e3 != null) {
                throw new RuntimeTraceRegressionSuiteDefinitionImportPreviewRejectedException("invalid zip");
            }
            return new byte[][] {manifestBytes, definitionBytes};
        } catch (IOException ex) {
            throw new RuntimeTraceRegressionSuiteDefinitionImportPreviewRejectedException("invalid zip", ex);
        }
    }

    private static boolean entryNameIsDirectory(String name) {
        return name != null && name.endsWith("/");
    }
}

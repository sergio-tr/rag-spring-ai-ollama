package com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionimport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionService;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.CreateDefinitionCommand;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionDetailDto;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import com.uniovi.rag.infrastructure.zip.ZipExpansionBudget;
import com.uniovi.rag.infrastructure.zip.ZipIoGuards;

import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * P39: ZIP import adapter (non-owning) — validates a P38-shaped ZIP, maps {@link RuntimeTraceRegressionSuiteDefinitionDetailDto}
 * to {@link CreateDefinitionCommand} via {@link RuntimeTraceRegressionSuiteDefinitionDetailDto#toCreateDefinitionCommand()}, then
 * delegates {@link RuntimeTraceRegressionSuiteDefinitionService#create(UUID, CreateDefinitionCommand)} only.
 */
@Service
public class RuntimeTraceRegressionSuiteDefinitionImportService {

    public static final long MAX_IMPORT_ZIP_BYTES = 2097152L;

    private final RuntimeTraceRegressionSuiteDefinitionService definitionService;
    private final ObjectMapper objectMapper;

    public RuntimeTraceRegressionSuiteDefinitionImportService(RuntimeTraceRegressionSuiteDefinitionService definitionService) {
        this.definitionService = definitionService;
        this.objectMapper =
                new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .disable(SerializationFeature.INDENT_OUTPUT)
                        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    /**
     * Parses and persists a new definition from a P38-strict ZIP for the given user.
     */
    public UUID importDefinitionZip(byte[] zipBytes, UUID userId) {
        if (zipBytes == null || zipBytes.length == 0 || zipBytes.length > MAX_IMPORT_ZIP_BYTES) {
            throw new RuntimeTraceRegressionSuiteDefinitionImportRejectedException("invalid zip");
        }
        byte[][] parts = readManifestAndDefinitionBytes(zipBytes);
        byte[] manifestBytes = parts[0];
        byte[] definitionBytes = parts[1];

        JsonNode manifestRoot;
        try {
            manifestRoot = objectMapper.readTree(manifestBytes);
        } catch (IOException ex) {
            throw new RuntimeTraceRegressionSuiteDefinitionImportRejectedException("invalid manifest", ex);
        }
        validateManifest(manifestRoot, zipBytes.length);

        RuntimeTraceRegressionSuiteDefinitionDetailDto detail;
        try {
            detail = objectMapper.readValue(definitionBytes, RuntimeTraceRegressionSuiteDefinitionDetailDto.class);
        } catch (IOException ex) {
            throw new RuntimeTraceRegressionSuiteDefinitionImportRejectedException("invalid definition.json", ex);
        }

        CreateDefinitionCommand command;
        try {
            command = detail.toCreateDefinitionCommand();
        } catch (IllegalArgumentException ex) {
            throw new RuntimeTraceRegressionSuiteDefinitionImportRejectedException("P39 import: invalid definition command", ex);
        }

        return definitionService.create(userId, command);
    }

    private void validateManifest(JsonNode root, int bodyLength) {
        if (!root.hasNonNull("exportKind")
                || !root.get("exportKind").isTextual()
                || !"REGRESSION_SUITE_DEFINITION".equals(root.get("exportKind").asText())) {
            throw new RuntimeTraceRegressionSuiteDefinitionImportRejectedException("invalid manifest");
        }
        if (!root.has("schemaVersion")
                || !root.get("schemaVersion").isIntegralNumber()
                || root.get("schemaVersion").intValue() != 1) {
            throw new RuntimeTraceRegressionSuiteDefinitionImportRejectedException("invalid manifest");
        }
        if (!root.has("truncated")
                || !root.get("truncated").isBoolean()
                || root.get("truncated").booleanValue()) {
            throw new RuntimeTraceRegressionSuiteDefinitionImportRejectedException("invalid manifest");
        }
        if (!root.has("zipSizeBytes")
                || !root.get("zipSizeBytes").isIntegralNumber()
                || root.get("zipSizeBytes").longValue() != (long) bodyLength) {
            throw new RuntimeTraceRegressionSuiteDefinitionImportRejectedException("invalid manifest");
        }
    }

    private static byte[][] readManifestAndDefinitionBytes(byte[] body) {
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(body))) {
            ZipExpansionBudget budget = ZipExpansionBudget.forUploadedZip(MAX_IMPORT_ZIP_BYTES);
            ZipEntry e1 = zin.getNextEntry();
            if (e1 == null || e1.isDirectory() || entryNameIsDirectory(e1.getName())) {
                throw new RuntimeTraceRegressionSuiteDefinitionImportRejectedException("invalid zip");
            }
            try {
                ZipIoGuards.requireSafeEntryName(e1.getName());
            } catch (IOException ex) {
                throw new RuntimeTraceRegressionSuiteDefinitionImportRejectedException("invalid zip", ex);
            }
            if (!"manifest.json".equals(e1.getName()) || e1.getMethod() != ZipEntry.STORED) {
                throw new RuntimeTraceRegressionSuiteDefinitionImportRejectedException("invalid zip");
            }
            byte[] manifestBytes = ZipIoGuards.readStoredEntryBytes(zin, e1, MAX_IMPORT_ZIP_BYTES, budget);
            zin.closeEntry();

            ZipEntry e2 = zin.getNextEntry();
            if (e2 == null || e2.isDirectory() || entryNameIsDirectory(e2.getName())) {
                throw new RuntimeTraceRegressionSuiteDefinitionImportRejectedException("invalid zip");
            }
            try {
                ZipIoGuards.requireSafeEntryName(e2.getName());
            } catch (IOException ex) {
                throw new RuntimeTraceRegressionSuiteDefinitionImportRejectedException("invalid zip", ex);
            }
            if (!"definition.json".equals(e2.getName()) || e2.getMethod() != ZipEntry.STORED) {
                throw new RuntimeTraceRegressionSuiteDefinitionImportRejectedException("invalid zip");
            }
            byte[] definitionBytes = ZipIoGuards.readStoredEntryBytes(zin, e2, MAX_IMPORT_ZIP_BYTES, budget);
            zin.closeEntry();

            ZipEntry e3 = zin.getNextEntry();
            if (e3 != null) {
                throw new RuntimeTraceRegressionSuiteDefinitionImportRejectedException("invalid zip");
            }
            return new byte[][] {manifestBytes, definitionBytes};
        } catch (IOException ex) {
            throw new RuntimeTraceRegressionSuiteDefinitionImportRejectedException("invalid zip", ex);
        }
    }

    private static boolean entryNameIsDirectory(String name) {
        return name != null && name.endsWith("/");
    }
}

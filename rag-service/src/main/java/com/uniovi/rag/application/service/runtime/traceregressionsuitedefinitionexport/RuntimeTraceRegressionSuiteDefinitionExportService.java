package com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionexport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionService;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionSnapshot;
import com.uniovi.rag.interfaces.rest.NotFoundException;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionDetailDto;
import org.springframework.stereotype.Service;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * P38: ZIP export adapter (non-owning) — builds {@code manifest.json} + {@code definition.json} after
 * {@link RuntimeTraceRegressionSuiteDefinitionService#loadByIdForUser(UUID, UUID)}; does not own persisted-definition reads.
 */
@Service
public class RuntimeTraceRegressionSuiteDefinitionExportService {

    public static final long MAX_ZIP_SIZE_BYTES = 2097152L;

    private static final int EXPORT_SCHEMA_VERSION = 1;
    private static final String EXPORT_KIND = "REGRESSION_SUITE_DEFINITION";
    private static final String SELECTOR_SAVED_DEFINITION_BY_ID = "SAVED_DEFINITION_BY_ID";

    private final RuntimeTraceRegressionSuiteDefinitionService definitionService;
    private final ObjectMapper objectMapper;
    private final long maxZipSizeBytes;

    public RuntimeTraceRegressionSuiteDefinitionExportService(RuntimeTraceRegressionSuiteDefinitionService definitionService) {
        this(definitionService, MAX_ZIP_SIZE_BYTES);
    }

    RuntimeTraceRegressionSuiteDefinitionExportService(
            RuntimeTraceRegressionSuiteDefinitionService definitionService, long maxZipSizeBytes) {
        this.definitionService = definitionService;
        this.maxZipSizeBytes = maxZipSizeBytes;
        this.objectMapper =
                new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .disable(SerializationFeature.INDENT_OUTPUT)
                        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    public RuntimeTraceRegressionSuiteDefinitionExportArtifact exportDefinitionZip(UUID definitionId, UUID userId) {
        Optional<RuntimeTraceRegressionSuiteDefinitionSnapshot> loaded =
                definitionService.loadByIdForUser(definitionId, userId);
        if (loaded.isEmpty()) {
            throw new NotFoundException("definition not found");
        }
        RuntimeTraceRegressionSuiteDefinitionSnapshot snapshot = loaded.get();
        byte[] definitionJsonBytes;
        try {
            definitionJsonBytes =
                    objectMapper.writeValueAsBytes(RuntimeTraceRegressionSuiteDefinitionDetailDto.fromSnapshot(snapshot));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to build definition export ZIP", ex);
        }
        Instant generatedAt = Instant.now();
        byte[] zipBytes = buildZipBytes(generatedAt, userId, definitionId, definitionJsonBytes);
        if (zipBytes.length > maxZipSizeBytes) {
            throw new RuntimeTraceRegressionSuiteDefinitionExportSizeExceededException("definition export exceeds max ZIP size");
        }
        String filename = "runtime-trace-regression-suite-definition_" + definitionId + ".zip";
        return new RuntimeTraceRegressionSuiteDefinitionExportArtifact(
                filename,
                RuntimeTraceRegressionSuiteDefinitionExportArtifact.MEDIA_TYPE_ZIP,
                zipBytes,
                zipBytes.length);
    }

    byte[] buildZipBytes(Instant generatedAt, UUID requestedByUserId, UUID definitionId, byte[] definitionJsonUtf8) {
        long candidateZipSizeBytes = 0L;
        String defIdStr = definitionId.toString();
        Map<String, String> scope = Map.of("definitionId", defIdStr);
        for (int i = 0; i < 64; i++) {
            RuntimeTraceRegressionSuiteDefinitionExportManifest manifest =
                    new RuntimeTraceRegressionSuiteDefinitionExportManifest(
                            EXPORT_SCHEMA_VERSION,
                            EXPORT_KIND,
                            generatedAt,
                            requestedByUserId.toString(),
                            SELECTOR_SAVED_DEFINITION_BY_ID,
                            scope,
                            defIdStr,
                            candidateZipSizeBytes,
                            false);
            byte[] zipBytes;
            try {
                byte[] manifestJson = objectMapper.writeValueAsBytes(manifest);
                zipBytes = writeZipToBytes(manifestJson, definitionJsonUtf8);
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to build definition export ZIP", ex);
            }
            if (manifest.zipSizeBytes() == zipBytes.length) {
                return zipBytes;
            }
            candidateZipSizeBytes = zipBytes.length;
        }
        throw new IllegalStateException("P38 definition export manifest zipSizeBytes did not converge");
    }

    private static byte[] writeZipToBytes(byte[] manifestJsonUtf8, byte[] definitionJsonUtf8) throws IOException {
        ByteArrayOutputStream baos =
                new ByteArrayOutputStream(manifestJsonUtf8.length + definitionJsonUtf8.length + 256);
        try (ZipOutputStream zos =
                        new ZipOutputStream(new BufferedOutputStream(baos), StandardCharsets.UTF_8)) {
            putStoredEntry(zos, "manifest.json", manifestJsonUtf8);
            putStoredEntry(zos, "definition.json", definitionJsonUtf8);
        }
        return baos.toByteArray();
    }

    private static void putStoredEntry(ZipOutputStream zos, String name, byte[] data) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(data.length);
        entry.setCompressedSize(data.length);
        CRC32 crc = new CRC32();
        crc.update(data);
        entry.setCrc(crc.getValue());
        zos.putNextEntry(entry);
        zos.write(data);
        zos.closeEntry();
    }
}

package com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionimport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionexport.RuntimeTraceRegressionSuiteDefinitionExportManifest;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds P38-strict STORED two-entry ZIPs for P39 import tests.
 */
public final class P39ImportZipTestUtil {

    private P39ImportZipTestUtil() {}

    public static ObjectMapper fd4ObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.INDENT_OUTPUT)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    public static byte[] buildConvergedP38Zip(
            ObjectMapper objectMapper, Instant generatedAt, UUID userId, UUID definitionId, byte[] definitionJsonUtf8)
            throws IOException {
        long candidateZipSizeBytes = 0L;
        String defIdStr = definitionId.toString();
        Map<String, String> scope = Map.of("definitionId", defIdStr);
        for (int i = 0; i < 64; i++) {
            RuntimeTraceRegressionSuiteDefinitionExportManifest manifest =
                    new RuntimeTraceRegressionSuiteDefinitionExportManifest(
                            1,
                            "REGRESSION_SUITE_DEFINITION",
                            generatedAt,
                            userId.toString(),
                            "SAVED_DEFINITION_BY_ID",
                            scope,
                            defIdStr,
                            candidateZipSizeBytes,
                            false);
            byte[] manifestJson = objectMapper.writeValueAsBytes(manifest);
            byte[] zipBytes = writeTwoStored(manifestJson, definitionJsonUtf8);
            if (manifest.zipSizeBytes() == zipBytes.length) {
                return zipBytes;
            }
            candidateZipSizeBytes = zipBytes.length;
        }
        throw new IllegalStateException("zipSizeBytes did not converge");
    }

    /**
     * P38-shaped ZIP with a third root STORED entry after {@code definition.json} (fails P39 FD-zip-read).
     */
    public static byte[] buildConvergedP38ZipWithThirdRootEntry(
            ObjectMapper objectMapper,
            Instant generatedAt,
            UUID userId,
            UUID definitionId,
            byte[] definitionJsonUtf8,
            byte[] thirdEntryPayload)
            throws IOException {
        long candidateZipSizeBytes = 0L;
        String defIdStr = definitionId.toString();
        Map<String, String> scope = Map.of("definitionId", defIdStr);
        for (int i = 0; i < 64; i++) {
            RuntimeTraceRegressionSuiteDefinitionExportManifest manifest =
                    new RuntimeTraceRegressionSuiteDefinitionExportManifest(
                            1,
                            "REGRESSION_SUITE_DEFINITION",
                            generatedAt,
                            userId.toString(),
                            "SAVED_DEFINITION_BY_ID",
                            scope,
                            defIdStr,
                            candidateZipSizeBytes,
                            false);
            byte[] manifestJson = objectMapper.writeValueAsBytes(manifest);
            byte[] zipBytes = writeThreeStored(manifestJson, definitionJsonUtf8, thirdEntryPayload);
            if (manifest.zipSizeBytes() == zipBytes.length) {
                return zipBytes;
            }
            candidateZipSizeBytes = zipBytes.length;
        }
        throw new IllegalStateException("zipSizeBytes did not converge");
    }

    public static byte[] writeTwoStored(String firstName, byte[] first, String secondName, byte[] second)
            throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(first.length + second.length + 256);
        try (ZipOutputStream zos =
                        new ZipOutputStream(new BufferedOutputStream(baos), StandardCharsets.UTF_8)) {
            putStoredEntry(zos, firstName, first);
            putStoredEntry(zos, secondName, second);
        }
        return baos.toByteArray();
    }

    public static byte[] writeTwoStored(byte[] manifestJson, byte[] definitionJson) throws IOException {
        return writeTwoStored("manifest.json", manifestJson, "definition.json", definitionJson);
    }

    public static byte[] writeThreeStored(byte[] manifestJson, byte[] definitionJson, byte[] thirdPayload)
            throws IOException {
        ByteArrayOutputStream baos =
                new ByteArrayOutputStream(manifestJson.length + definitionJson.length + thirdPayload.length + 256);
        try (ZipOutputStream zos =
                        new ZipOutputStream(new BufferedOutputStream(baos), StandardCharsets.UTF_8)) {
            putStoredEntry(zos, "manifest.json", manifestJson);
            putStoredEntry(zos, "definition.json", definitionJson);
            putStoredEntry(zos, "extra.root", thirdPayload);
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

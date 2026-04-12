package com.uniovi.rag.application.service.runtime.traceregressionsuiterunimport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Builds P43-shaped STORED ZIPs for P44 WebMvc tests (manifest {@code zipSizeBytes} converges to final body length). */
public final class RunImportZipTestUtil {

    static final ObjectMapper FD4 =
            new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .disable(SerializationFeature.INDENT_OUTPUT)
                    .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    private RunImportZipTestUtil() {}

    /**
     * Reads {@code run.json} bytes from a valid two-entry STORED ZIP (manifest first, run second).
     */
    public static byte[] extractRunJsonBytes(byte[] zip) throws IOException {
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e1 = zin.getNextEntry();
            zin.readNBytes((int) e1.getSize());
            zin.closeEntry();
            ZipEntry e2 = zin.getNextEntry();
            return zin.readNBytes((int) e2.getSize());
        }
    }

    public static byte[] buildAdHocEmptyRunZip(UUID runId, UUID userIdForManifestIgnored) throws IOException {
        Instant createdAt = Instant.parse("2024-06-01T12:00:00Z");
        ObjectNode run =
                FD4.createObjectNode();
        run.put("id", runId.toString());
        run.put("sourceType", "AD_HOC");
        run.putNull("definitionId");
        run.put("suiteOutcome", "COMPLETED_ALL_BATCH_RETURNS");
        run.put("createdAt", createdAt.toString());
        run.put("requestedEntryCount", 0);
        run.put("processedEntryCount", 0);
        run.put("batchReturnedCount", 0);
        run.put("executionFailedCount", 0);
        run.put("batchNotAttemptedSubcount", 0);
        run.putArray("entries");
        byte[] runBytes = FD4.writeValueAsBytes(run);

        ObjectNode scope = FD4.createObjectNode();
        scope.put("runId", runId.toString());

        ObjectNode manifest = FD4.createObjectNode();
        manifest.put("schemaVersion", 1);
        manifest.put("exportKind", "REGRESSION_SUITE_RUN");
        manifest.put("generatedAt", Instant.parse("2024-06-01T10:00:00Z").toString());
        manifest.put("requestedByUserId", userIdForManifestIgnored.toString());
        manifest.put("selectorType", "SAVED_RUN_BY_ID");
        manifest.set("scope", scope);
        manifest.put("runId", runId.toString());
        manifest.put("sourceType", "AD_HOC");
        manifest.putNull("definitionId");
        manifest.put("suiteOutcome", "COMPLETED_ALL_BATCH_RETURNS");
        manifest.put("requestedEntryCount", 0);
        manifest.put("processedEntryCount", 0);
        manifest.put("batchReturnedCount", 0);
        manifest.put("executionFailedCount", 0);
        manifest.put("batchNotAttemptedSubcount", 0);
        manifest.put("truncated", false);

        long candidate = 0L;
        for (int i = 0; i < 64; i++) {
            manifest.put("zipSizeBytes", candidate);
            byte[] manifestBytes = FD4.writeValueAsBytes(manifest);
            byte[] zip = zipStored("manifest.json", manifestBytes, "run.json", runBytes);
            if (manifest.get("zipSizeBytes").asLong() == zip.length) {
                return zip;
            }
            candidate = zip.length;
        }
        throw new IllegalStateException("zipSizeBytes did not converge");
    }

    /**
     * Valid manifest + invalid UTF-8 JSON for {@code run.json} (fails at {@code readValue}).
     */
    public static byte[] buildZipWithInvalidRunJson(UUID runId, UUID userId) throws IOException {
        byte[] runBytes = "{".getBytes(StandardCharsets.UTF_8);
        ObjectNode scope = FD4.createObjectNode();
        scope.put("runId", runId.toString());
        ObjectNode manifest = baseManifestAdHocEmpty(runId, userId);
        manifest.set("scope", scope);
        return convergeZip(manifest, runBytes);
    }

    /**
     * Manifest {@code suiteOutcome} does not match {@code run.json} detail (fails FD-coherence).
     */
    public static byte[] buildZipWithCoherenceMismatch(UUID runId, UUID userId) throws IOException {
        Instant createdAt = Instant.parse("2024-06-01T12:00:00Z");
        ObjectNode run =
                FD4.createObjectNode();
        run.put("id", runId.toString());
        run.put("sourceType", "AD_HOC");
        run.putNull("definitionId");
        run.put("suiteOutcome", "COMPLETED_ALL_BATCH_RETURNS");
        run.put("createdAt", createdAt.toString());
        run.put("requestedEntryCount", 0);
        run.put("processedEntryCount", 0);
        run.put("batchReturnedCount", 0);
        run.put("executionFailedCount", 0);
        run.put("batchNotAttemptedSubcount", 0);
        run.putArray("entries");
        byte[] runBytes = FD4.writeValueAsBytes(run);

        ObjectNode scope = FD4.createObjectNode();
        scope.put("runId", runId.toString());
        ObjectNode manifest = baseManifestAdHocEmpty(runId, userId);
        manifest.set("scope", scope);
        manifest.put("suiteOutcome", "EMPTY_SUITE");
        return convergeZip(manifest, runBytes);
    }

    private static ObjectNode baseManifestAdHocEmpty(UUID runId, UUID userId) {
        ObjectNode manifest = FD4.createObjectNode();
        manifest.put("schemaVersion", 1);
        manifest.put("exportKind", "REGRESSION_SUITE_RUN");
        manifest.put("generatedAt", Instant.parse("2024-06-01T10:00:00Z").toString());
        manifest.put("requestedByUserId", userId.toString());
        manifest.put("selectorType", "SAVED_RUN_BY_ID");
        manifest.put("runId", runId.toString());
        manifest.put("sourceType", "AD_HOC");
        manifest.putNull("definitionId");
        manifest.put("suiteOutcome", "COMPLETED_ALL_BATCH_RETURNS");
        manifest.put("requestedEntryCount", 0);
        manifest.put("processedEntryCount", 0);
        manifest.put("batchReturnedCount", 0);
        manifest.put("executionFailedCount", 0);
        manifest.put("batchNotAttemptedSubcount", 0);
        manifest.put("truncated", false);
        return manifest;
    }

    private static byte[] convergeZip(ObjectNode manifest, byte[] runBytes) throws IOException {
        long candidate = 0L;
        for (int i = 0; i < 64; i++) {
            manifest.put("zipSizeBytes", candidate);
            byte[] manifestBytes = FD4.writeValueAsBytes(manifest);
            byte[] zip = zipStored("manifest.json", manifestBytes, "run.json", runBytes);
            if (manifest.get("zipSizeBytes").asLong() == zip.length) {
                return zip;
            }
            candidate = zip.length;
        }
        throw new IllegalStateException("zipSizeBytes did not converge");
    }

    /**
     * AD_HOC with non-null {@code definitionId} in manifest and run.json (coherent; fails FD-source-rules).
     */
    public static byte[] buildZipAdHocWithDefinitionId(UUID runId, UUID userId, UUID definitionId) throws IOException {
        Instant createdAt = Instant.parse("2024-06-01T12:00:00Z");
        ObjectNode run = FD4.createObjectNode();
        run.put("id", runId.toString());
        run.put("sourceType", "AD_HOC");
        run.put("definitionId", definitionId.toString());
        run.put("suiteOutcome", "COMPLETED_ALL_BATCH_RETURNS");
        run.put("createdAt", createdAt.toString());
        run.put("requestedEntryCount", 0);
        run.put("processedEntryCount", 0);
        run.put("batchReturnedCount", 0);
        run.put("executionFailedCount", 0);
        run.put("batchNotAttemptedSubcount", 0);
        run.putArray("entries");
        byte[] runBytes = FD4.writeValueAsBytes(run);

        ObjectNode scope = FD4.createObjectNode();
        scope.put("runId", runId.toString());
        ObjectNode manifest = baseManifestAdHocEmpty(runId, userId);
        manifest.set("scope", scope);
        manifest.remove("definitionId");
        manifest.put("definitionId", definitionId.toString());
        return convergeZip(manifest, runBytes);
    }

    /**
     * SAVED_DEFINITION with null {@code definitionId} in manifest and run.json (coherent; fails FD-source-rules).
     */
    public static byte[] buildZipSavedDefinitionWithNullDefinitionId(UUID runId, UUID userId) throws IOException {
        Instant createdAt = Instant.parse("2024-06-01T12:00:00Z");
        ObjectNode run = FD4.createObjectNode();
        run.put("id", runId.toString());
        run.put("sourceType", "SAVED_DEFINITION");
        run.putNull("definitionId");
        run.put("suiteOutcome", "COMPLETED_ALL_BATCH_RETURNS");
        run.put("createdAt", createdAt.toString());
        run.put("requestedEntryCount", 0);
        run.put("processedEntryCount", 0);
        run.put("batchReturnedCount", 0);
        run.put("executionFailedCount", 0);
        run.put("batchNotAttemptedSubcount", 0);
        run.putArray("entries");
        byte[] runBytes = FD4.writeValueAsBytes(run);

        ObjectNode scope = FD4.createObjectNode();
        scope.put("runId", runId.toString());
        ObjectNode manifest = baseManifestAdHocEmpty(runId, userId);
        manifest.set("scope", scope);
        manifest.remove("sourceType");
        manifest.remove("definitionId");
        manifest.put("sourceType", "SAVED_DEFINITION");
        manifest.putNull("definitionId");
        return convergeZip(manifest, runBytes);
    }

    public static byte[] zipStored(String n1, byte[] c1, String n2, byte[] c2) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(c1.length + c2.length + 128);
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(baos), StandardCharsets.UTF_8)) {
            putStored(zos, n1, c1);
            putStored(zos, n2, c2);
        }
        return baos.toByteArray();
    }

    private static void putStored(ZipOutputStream zos, String name, byte[] data) throws IOException {
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

    /** ZIP with first entry DEFLATED (invalid for P44). */
    public static byte[] buildZipWithDeflatedFirstEntry(byte[] manifestBytes, byte[] runBytes) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(baos), StandardCharsets.UTF_8)) {
            ZipEntry e1 = new ZipEntry("manifest.json");
            e1.setMethod(ZipEntry.DEFLATED);
            zos.putNextEntry(e1);
            zos.write(manifestBytes);
            zos.closeEntry();
            putStored(zos, "run.json", runBytes);
        }
        return baos.toByteArray();
    }

    /** Three STORED entries (invalid). */
    public static byte[] buildZipWithThreeEntries(byte[] manifestBytes, byte[] runBytes) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(baos), StandardCharsets.UTF_8)) {
            putStored(zos, "manifest.json", manifestBytes);
            putStored(zos, "run.json", runBytes);
            putStored(zos, "extra.txt", "x".getBytes(StandardCharsets.UTF_8));
        }
        return baos.toByteArray();
    }

    /** Wrong order: run.json first. */
    public static byte[] buildZipWrongOrder(byte[] manifestBytes, byte[] runBytes) throws IOException {
        return zipStored("run.json", runBytes, "manifest.json", manifestBytes);
    }
}

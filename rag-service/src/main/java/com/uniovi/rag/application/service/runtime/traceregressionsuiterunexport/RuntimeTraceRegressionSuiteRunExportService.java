package com.uniovi.rag.application.service.runtime.traceregressionsuiterunexport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunPersistenceService;
import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunSnapshot;
import com.uniovi.rag.interfaces.rest.NotFoundException;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunDetailDto;
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
 * P43: ZIP export adapter (non-owning) — builds {@code manifest.json} + {@code run.json} after
 * {@link RuntimeTraceRegressionSuiteRunPersistenceService#loadByIdForUser(java.util.UUID, java.util.UUID)}; does not own
 * storage-backed reads of persisted runs.
 */
@Service
public class RuntimeTraceRegressionSuiteRunExportService {

    public static final long MAX_ZIP_SIZE_BYTES = 2097152L;

    private static final int EXPORT_SCHEMA_VERSION = 1;
    private static final String EXPORT_KIND = "REGRESSION_SUITE_RUN";
    private static final String SELECTOR_SAVED_RUN_BY_ID = "SAVED_RUN_BY_ID";

    private final RuntimeTraceRegressionSuiteRunPersistenceService runPersistenceService;
    private final ObjectMapper objectMapper;
    private final long maxZipSizeBytes;

    public RuntimeTraceRegressionSuiteRunExportService(RuntimeTraceRegressionSuiteRunPersistenceService runPersistenceService) {
        this(runPersistenceService, MAX_ZIP_SIZE_BYTES);
    }

    RuntimeTraceRegressionSuiteRunExportService(
            RuntimeTraceRegressionSuiteRunPersistenceService runPersistenceService, long maxZipSizeBytes) {
        this.runPersistenceService = runPersistenceService;
        this.maxZipSizeBytes = maxZipSizeBytes;
        this.objectMapper =
                new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .disable(SerializationFeature.INDENT_OUTPUT)
                        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    public RuntimeTraceRegressionSuiteRunExportArtifact exportRunZip(UUID runId, UUID userId) {
        Optional<RuntimeTraceRegressionSuiteRunSnapshot> loaded = runPersistenceService.loadByIdForUser(runId, userId);
        if (loaded.isEmpty()) {
            throw new NotFoundException("run not found");
        }
        RuntimeTraceRegressionSuiteRunSnapshot snapshot = loaded.get();
        RuntimeTraceRegressionSuiteRunDetailDto detailDto = RuntimeTraceRegressionSuiteRunDetailDto.fromSnapshot(snapshot);
        byte[] runJsonBytes;
        try {
            runJsonBytes = objectMapper.writeValueAsBytes(detailDto);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to build run export ZIP", ex);
        }
        Instant generatedAt = Instant.now();
        byte[] zipBytes = buildZipBytes(generatedAt, userId, runId, snapshot, runJsonBytes);
        if (zipBytes.length > maxZipSizeBytes) {
            throw new RuntimeTraceRegressionSuiteRunExportSizeExceededException("run export exceeds max ZIP size");
        }
        String filename = "runtime-trace-regression-suite-run_" + runId + ".zip";
        return new RuntimeTraceRegressionSuiteRunExportArtifact(
                filename,
                RuntimeTraceRegressionSuiteRunExportArtifact.MEDIA_TYPE_ZIP,
                zipBytes,
                zipBytes.length);
    }

    byte[] buildZipBytes(
            Instant generatedAt,
            UUID requestedByUserId,
            UUID pathRunId,
            RuntimeTraceRegressionSuiteRunSnapshot snapshot,
            byte[] runJsonUtf8) {
        long candidateZipSizeBytes = 0L;
        Map<String, String> scope = Map.of("runId", pathRunId.toString());
        var sum = snapshot.summary();
        for (int i = 0; i < 64; i++) {
            RuntimeTraceRegressionSuiteRunExportManifest manifest =
                    new RuntimeTraceRegressionSuiteRunExportManifest(
                            EXPORT_SCHEMA_VERSION,
                            EXPORT_KIND,
                            generatedAt,
                            requestedByUserId.toString(),
                            SELECTOR_SAVED_RUN_BY_ID,
                            scope,
                            snapshot.id().value().toString(),
                            snapshot.sourceType().name(),
                            snapshot.definitionId().map(UUID::toString).orElse(null),
                            snapshot.suiteOutcome().name(),
                            sum.requestedEntryCount(),
                            sum.processedEntryCount(),
                            sum.batchReturnedCount(),
                            sum.executionFailedCount(),
                            sum.batchNotAttemptedSubcount(),
                            candidateZipSizeBytes,
                            false);
            byte[] zipBytes;
            try {
                byte[] manifestJson = objectMapper.writeValueAsBytes(manifest);
                zipBytes = writeZipToBytes(manifestJson, runJsonUtf8);
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to build run export ZIP", ex);
            }
            if (manifest.zipSizeBytes() == zipBytes.length) {
                return zipBytes;
            }
            candidateZipSizeBytes = zipBytes.length;
        }
        throw new IllegalStateException("P43 run export manifest zipSizeBytes did not converge");
    }

    private static byte[] writeZipToBytes(byte[] manifestJsonUtf8, byte[] runJsonUtf8) throws IOException {
        ByteArrayOutputStream baos =
                new ByteArrayOutputStream(manifestJsonUtf8.length + runJsonUtf8.length + 256);
        try (ZipOutputStream zos =
                        new ZipOutputStream(new BufferedOutputStream(baos), StandardCharsets.UTF_8)) {
            putStoredEntry(zos, "manifest.json", manifestJsonUtf8);
            putStoredEntry(zos, "run.json", runJsonUtf8);
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

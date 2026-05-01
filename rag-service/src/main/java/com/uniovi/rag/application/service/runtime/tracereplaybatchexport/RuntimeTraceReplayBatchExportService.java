package com.uniovi.rag.application.service.runtime.tracereplaybatchexport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.uniovi.rag.application.service.runtime.tracereplaybatch.RuntimeTraceReplayBatchService;
import com.uniovi.rag.domain.runtime.tracereplaybatch.RuntimeTraceReplayBatchMode;
import com.uniovi.rag.domain.runtime.tracereplaybatch.RuntimeTraceReplayBatchOutcome;
import com.uniovi.rag.domain.runtime.tracereplaybatch.RuntimeTraceReplayBatchRequest;
import com.uniovi.rag.domain.runtime.tracereplaybatch.RuntimeTraceReplayBatchResult;
import com.uniovi.rag.interfaces.rest.dto.tracereplaybatch.RuntimeTraceReplayBatchResponseDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * P29: packages one {@link RuntimeTraceReplayBatchService#execute} result as {@code manifest.json} + {@code batch.json}
 * (from {@link RuntimeTraceReplayBatchResponseDto#fromBatchResult} only).
 */
@Service
public class RuntimeTraceReplayBatchExportService {

    public static final long MAX_ZIP_SIZE_BYTES = 2097152L;
    public static final int EXPORT_SCHEMA_VERSION = 1;
    public static final String EXPORT_KIND = "REPLAY_BATCH";

    private final RuntimeTraceReplayBatchService batchService;
    private final ObjectMapper objectMapper;
    private final long maxZipSizeBytes;

    @Autowired
    public RuntimeTraceReplayBatchExportService(RuntimeTraceReplayBatchService batchService) {
        this(batchService, MAX_ZIP_SIZE_BYTES);
    }

    /**
     * Package-private: production uses {@link #MAX_ZIP_SIZE_BYTES}; tests may pass a smaller cap to assert {@code 413}.
     */
    RuntimeTraceReplayBatchExportService(RuntimeTraceReplayBatchService batchService, long maxZipSizeBytes) {
        this.batchService = batchService;
        this.maxZipSizeBytes = maxZipSizeBytes;
        this.objectMapper =
                new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .disable(SerializationFeature.INDENT_OUTPUT)
                        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    /**
     * Route 1: ordered raw {@code traceIds} list (post-validation) for manifest scope.
     */
    public RuntimeTraceReplayBatchExportArtifact exportByTraceIds(UUID userId, List<UUID> rawTraceIdsOrdered) {
        RuntimeTraceReplayBatchRequest request = RuntimeTraceReplayBatchRequest.byTraceIds(userId, rawTraceIdsOrdered);
        RuntimeTraceReplayBatchResult result = batchService.execute(request);
        throwIfNotExportable(result);
        try {
            return buildArtifact(
                    userId,
                    RuntimeTraceReplayBatchMode.BY_TRACE_IDS,
                    result,
                    "runtime-trace-replays-batch.zip",
                    new ReplayBatchExportManifest.Scope(List.copyOf(rawTraceIdsOrdered), null, null, null, null));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to build batch export ZIP", e);
        }
    }

    public RuntimeTraceReplayBatchExportArtifact exportByConversation(
            UUID userId,
            UUID conversationId,
            Optional<Instant> createdAtFrom,
            Optional<Instant> createdAtTo,
            Optional<String> workflowName) {
        RuntimeTraceReplayBatchRequest request =
                RuntimeTraceReplayBatchRequest.byConversation(
                        userId, conversationId, createdAtFrom, createdAtTo, workflowName);
        RuntimeTraceReplayBatchResult result = batchService.execute(request);
        throwIfNotExportable(result);
        String wf = workflowName.map(String::trim).filter(s -> !s.isEmpty()).orElse(null);
        Instant from = createdAtFrom.orElse(null);
        Instant to = createdAtTo.orElse(null);
        try {
            return buildArtifact(
                    userId,
                    RuntimeTraceReplayBatchMode.BY_CONVERSATION,
                    result,
                    "runtime-trace-replays-batch_conversation_" + conversationId + ".zip",
                    new ReplayBatchExportManifest.Scope(null, conversationId, from, to, wf));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to build batch export ZIP", e);
        }
    }

    private static void throwIfNotExportable(RuntimeTraceReplayBatchResult result) {
        if (result.batchOutcome() == RuntimeTraceReplayBatchOutcome.NOT_ATTEMPTED) {
            throw new RuntimeTraceReplayBatchExportNotAttemptedException("batch outcome is NOT_ATTEMPTED");
        }
    }

    private RuntimeTraceReplayBatchExportArtifact buildArtifact(
            UUID requestedByUserId,
            RuntimeTraceReplayBatchMode mode,
            RuntimeTraceReplayBatchResult result,
            String filename,
            ReplayBatchExportManifest.Scope scope)
            throws IOException {
        RuntimeTraceReplayBatchResponseDto batchDto = RuntimeTraceReplayBatchResponseDto.fromBatchResult(mode, result);
        byte[] batchJsonBytes = objectMapper.writeValueAsBytes(batchDto);

        Instant generatedAt = Instant.now();
        String selectorType = mode.name();
        String batchOutcome = result.batchOutcome().name();
        int requestedCount = result.requestedCount();
        int selectedCount = result.selectedCount();
        int processedCount = result.summary().processedCount();

        byte[] zipBytes =
                buildZipWithStableManifestSize(
                        generatedAt,
                        requestedByUserId,
                        selectorType,
                        scope,
                        batchOutcome,
                        requestedCount,
                        selectedCount,
                        processedCount,
                        batchJsonBytes);

        if (zipBytes.length > maxZipSizeBytes) {
            throw new RuntimeTraceReplayBatchExportSizeExceededException("Batch export exceeds max ZIP size");
        }
        return new RuntimeTraceReplayBatchExportArtifact(
                filename, RuntimeTraceReplayBatchExportArtifact.MEDIA_TYPE_ZIP, zipBytes, zipBytes.length);
    }

    /**
     * Fixed-point: {@code manifest.zipSizeBytes} must equal the final archive length (same value as HTTP {@code
     * Content-Length}).
     */
    private byte[] buildZipWithStableManifestSize(
            Instant generatedAt,
            UUID requestedByUserId,
            String selectorType,
            ReplayBatchExportManifest.Scope scope,
            String batchOutcome,
            int requestedCount,
            int selectedCount,
            int processedCount,
            byte[] batchJsonBytes)
            throws IOException {
        long candidateZipSizeBytes = 0L;
        for (int i = 0; i < 64; i++) {
            ReplayBatchExportManifest manifest =
                    new ReplayBatchExportManifest(
                            EXPORT_SCHEMA_VERSION,
                            EXPORT_KIND,
                            generatedAt,
                            requestedByUserId,
                            selectorType,
                            scope,
                            batchOutcome,
                            requestedCount,
                            selectedCount,
                            processedCount,
                            candidateZipSizeBytes,
                            false);
            byte[] zipBytes = writeZipBytes(manifest, batchJsonBytes);
            if (manifest.zipSizeBytes() == zipBytes.length) {
                return zipBytes;
            }
            candidateZipSizeBytes = zipBytes.length;
        }
        throw new IllegalStateException("Batch export manifest zipSizeBytes did not converge");
    }

    private byte[] writeZipBytes(ReplayBatchExportManifest manifest, byte[] batchJsonBytes) throws IOException {
        byte[] manifestJson = objectMapper.writeValueAsBytes(manifest);
        ByteArrayOutputStream baos =
                new ByteArrayOutputStream(manifestJson.length + batchJsonBytes.length + 256);
        try (ZipOutputStream zos =
                        new ZipOutputStream(new BufferedOutputStream(baos), StandardCharsets.UTF_8)) {
            putStoredEntry(zos, "manifest.json", manifestJson);
            putStoredEntry(zos, "batch.json", batchJsonBytes);
        }
        return baos.toByteArray();
    }

    private static void putStoredEntry(ZipOutputStream zos, String name, byte[] data) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(data.length);
        CRC32 crc = new CRC32();
        crc.update(data);
        entry.setCrc(crc.getValue());
        zos.putNextEntry(entry);
        zos.write(data);
        zos.closeEntry();
    }
}

package com.uniovi.rag.application.service.runtime.tracecomparisonbatchexport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.uniovi.rag.application.service.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchService;
import com.uniovi.rag.domain.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchMode;
import com.uniovi.rag.domain.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchOutcome;
import com.uniovi.rag.domain.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchRequest;
import com.uniovi.rag.domain.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchResult;
import com.uniovi.rag.interfaces.rest.dto.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchResponseDto;
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
 * P26: packages one {@link RuntimeTraceReplayComparisonBatchService#execute} result as {@code manifest.json} +
 * {@code batch.json} (from {@link RuntimeTraceReplayComparisonBatchResponseDto#fromBatchResult} only).
 */
@Service
public class RuntimeTraceReplayComparisonBatchExportService {

    public static final long MAX_ZIP_SIZE_BYTES = 2097152L;
    public static final int EXPORT_SCHEMA_VERSION = 1;
    public static final String EXPORT_KIND = "REPLAY_COMPARISON_BATCH";

    private final RuntimeTraceReplayComparisonBatchService batchService;
    private final ObjectMapper objectMapper;
    private final long maxZipSizeBytes;

    public RuntimeTraceReplayComparisonBatchExportService(RuntimeTraceReplayComparisonBatchService batchService) {
        this(batchService, MAX_ZIP_SIZE_BYTES);
    }

    /**
     * Package-private: production uses {@link #MAX_ZIP_SIZE_BYTES}; tests may pass a smaller cap to assert {@code 413}.
     */
    RuntimeTraceReplayComparisonBatchExportService(
            RuntimeTraceReplayComparisonBatchService batchService, long maxZipSizeBytes) {
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
    public RuntimeTraceReplayComparisonBatchExportArtifact exportByTraceIds(UUID userId, List<UUID> rawTraceIdsOrdered) {
        RuntimeTraceReplayComparisonBatchRequest request =
                RuntimeTraceReplayComparisonBatchRequest.byTraceIds(userId, rawTraceIdsOrdered);
        RuntimeTraceReplayComparisonBatchResult result = batchService.execute(request);
        throwIfNotExportable(result);
        try {
            return buildArtifact(
                    userId,
                    RuntimeTraceReplayComparisonBatchMode.BY_TRACE_IDS,
                    result,
                    "runtime-trace-replay-comparisons-batch.zip",
                    new ReplayComparisonBatchExportManifest.Scope(
                            List.copyOf(rawTraceIdsOrdered), null, null, null, null));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to build batch export ZIP", e);
        }
    }

    public RuntimeTraceReplayComparisonBatchExportArtifact exportByConversation(
            UUID userId,
            UUID conversationId,
            Optional<Instant> createdAtFrom,
            Optional<Instant> createdAtTo,
            Optional<String> workflowName) {
        RuntimeTraceReplayComparisonBatchRequest request =
                RuntimeTraceReplayComparisonBatchRequest.byConversation(
                        userId, conversationId, createdAtFrom, createdAtTo, workflowName);
        RuntimeTraceReplayComparisonBatchResult result = batchService.execute(request);
        throwIfNotExportable(result);
        String wf = workflowName.map(String::trim).filter(s -> !s.isEmpty()).orElse(null);
        Instant from = createdAtFrom.orElse(null);
        Instant to = createdAtTo.orElse(null);
        try {
            return buildArtifact(
                    userId,
                    RuntimeTraceReplayComparisonBatchMode.BY_CONVERSATION,
                    result,
                    "runtime-trace-replay-comparisons-batch_conversation_" + conversationId + ".zip",
                    new ReplayComparisonBatchExportManifest.Scope(null, conversationId, from, to, wf));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to build batch export ZIP", e);
        }
    }

    private static void throwIfNotExportable(RuntimeTraceReplayComparisonBatchResult result) {
        if (result.batchOutcome() == RuntimeTraceReplayComparisonBatchOutcome.NOT_ATTEMPTED) {
            throw new RuntimeTraceReplayComparisonBatchExportNotAttemptedException("batch outcome is NOT_ATTEMPTED");
        }
    }

    private RuntimeTraceReplayComparisonBatchExportArtifact buildArtifact(
            UUID requestedByUserId,
            RuntimeTraceReplayComparisonBatchMode mode,
            RuntimeTraceReplayComparisonBatchResult result,
            String filename,
            ReplayComparisonBatchExportManifest.Scope scope)
            throws IOException {
        RuntimeTraceReplayComparisonBatchResponseDto batchDto =
                RuntimeTraceReplayComparisonBatchResponseDto.fromBatchResult(mode, result);
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
            throw new RuntimeTraceReplayComparisonBatchExportSizeExceededException("Batch export exceeds max ZIP size");
        }
        return new RuntimeTraceReplayComparisonBatchExportArtifact(
                filename,
                RuntimeTraceReplayComparisonBatchExportArtifact.MEDIA_TYPE_ZIP,
                zipBytes,
                zipBytes.length);
    }

    /**
     * Fixed-point: {@code manifest.zipSizeBytes} must equal the final archive length (same value as HTTP
     * {@code Content-Length}).
     */
    private byte[] buildZipWithStableManifestSize(
            Instant generatedAt,
            UUID requestedByUserId,
            String selectorType,
            ReplayComparisonBatchExportManifest.Scope scope,
            String batchOutcome,
            int requestedCount,
            int selectedCount,
            int processedCount,
            byte[] batchJsonBytes)
            throws IOException {
        long candidateZipSizeBytes = 0L;
        for (int i = 0; i < 64; i++) {
            ReplayComparisonBatchExportManifest manifest =
                    new ReplayComparisonBatchExportManifest(
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

    private byte[] writeZipBytes(ReplayComparisonBatchExportManifest manifest, byte[] batchJsonBytes)
            throws IOException {
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

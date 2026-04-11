package com.uniovi.rag.application.service.runtime.tracereplayexport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.uniovi.rag.application.service.runtime.tracereplay.RuntimeTraceReplayService;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayRequest;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayResult;
import com.uniovi.rag.interfaces.rest.dto.tracereplay.RuntimeTraceReplayResponseDto;
import org.springframework.stereotype.Service;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * P23: sole owner of standalone replay ZIP packaging. Calls {@link RuntimeTraceReplayService#replay} exactly once per
 * request; {@code replay.json} is built only from {@link RuntimeTraceReplayResponseDto#fromReplayHttp}.
 */
@Service
public class RuntimeTraceReplayExportService {

    public static final long MAX_ZIP_SIZE_BYTES = 2097152L;
    public static final int EXPORT_SCHEMA_VERSION = 1;
    public static final String EXPORT_KIND = "REPLAY";

    private final RuntimeTraceReplayService replayService;
    private final ObjectMapper objectMapper;
    private final long maxZipSizeBytes;

    public RuntimeTraceReplayExportService(RuntimeTraceReplayService replayService) {
        this(replayService, MAX_ZIP_SIZE_BYTES);
    }

    /**
     * Package-private: production uses {@link #MAX_ZIP_SIZE_BYTES}; tests may pass a smaller cap to assert 413.
     */
    RuntimeTraceReplayExportService(RuntimeTraceReplayService replayService, long maxZipSizeBytes) {
        this.replayService = replayService;
        this.maxZipSizeBytes = maxZipSizeBytes;
        this.objectMapper =
                new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .disable(SerializationFeature.INDENT_OUTPUT)
                        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    public RuntimeTraceReplayExportArtifact exportByTraceId(UUID userId, UUID traceId) {
        RuntimeTraceReplayRequest request = RuntimeTraceReplayRequest.byTraceId(userId, traceId);
        RuntimeTraceReplayResult result = replayService.replay(request);
        try {
            return buildArtifact(
                    result,
                    request,
                    traceId,
                    null,
                    null,
                    "runtime-trace-replay_" + traceId + ".zip");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to build replay export ZIP", e);
        }
    }

    public RuntimeTraceReplayExportArtifact exportByMessageId(
            UUID userId, UUID conversationId, UUID messageId) {
        RuntimeTraceReplayRequest request =
                RuntimeTraceReplayRequest.byMessageId(userId, conversationId, messageId);
        RuntimeTraceReplayResult result = replayService.replay(request);
        try {
            return buildArtifact(
                    result,
                    request,
                    null,
                    conversationId,
                    messageId,
                    "runtime-trace-replay_message_" + messageId + ".zip");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to build replay export ZIP", e);
        }
    }

    private RuntimeTraceReplayExportArtifact buildArtifact(
            RuntimeTraceReplayResult result,
            RuntimeTraceReplayRequest request,
            UUID pathTraceId,
            UUID pathConversationId,
            UUID pathMessageId,
            String filename)
            throws IOException {
        RuntimeTraceReplayResponseDto dto =
                RuntimeTraceReplayResponseDto.fromReplayHttp(result, request, pathTraceId, pathConversationId, pathMessageId);
        byte[] replayJsonBytes = objectMapper.writeValueAsBytes(dto);

        Instant generatedAt = Instant.now();
        UUID userId = request.userId();
        String selectorType = request.mode().name();
        RuntimeTraceReplayExportManifest.Scope scope =
                switch (request.mode()) {
                    case BY_TRACE_ID ->
                            new RuntimeTraceReplayExportManifest.Scope(
                                    request.traceId().orElseThrow(), null, null);
                    case BY_MESSAGE_ID ->
                            new RuntimeTraceReplayExportManifest.Scope(
                                    null, request.conversationId().orElseThrow(), request.messageId().orElseThrow());
                };
        String replayOutcome = result.outcome().name();

        byte[] zipBytes = buildZipWithStableManifestSize(
                generatedAt, userId, selectorType, scope, replayOutcome, replayJsonBytes);

        if (zipBytes.length > maxZipSizeBytes) {
            throw new RuntimeTraceReplayExportSizeExceededException("Replay export exceeds max ZIP size");
        }
        return new RuntimeTraceReplayExportArtifact(
                filename, RuntimeTraceReplayExportArtifact.MEDIA_TYPE_ZIP, zipBytes, zipBytes.length);
    }

    /**
     * Fixed-point: {@code manifest.zipSizeBytes} must equal the final archive length (same value as HTTP
     * Content-Length).
     */
    private byte[] buildZipWithStableManifestSize(
            Instant generatedAt,
            UUID requestedByUserId,
            String selectorType,
            RuntimeTraceReplayExportManifest.Scope scope,
            String replayOutcome,
            byte[] replayJsonBytes)
            throws IOException {
        long candidateZipSizeBytes = 0L;
        for (int i = 0; i < 64; i++) {
            RuntimeTraceReplayExportManifest manifest =
                    new RuntimeTraceReplayExportManifest(
                            EXPORT_SCHEMA_VERSION,
                            EXPORT_KIND,
                            generatedAt,
                            requestedByUserId,
                            selectorType,
                            scope,
                            replayOutcome,
                            candidateZipSizeBytes,
                            false);
            byte[] zipBytes = writeZipBytes(manifest, replayJsonBytes);
            if (manifest.zipSizeBytes() == zipBytes.length) {
                return zipBytes;
            }
            candidateZipSizeBytes = zipBytes.length;
        }
        throw new IllegalStateException("Replay export manifest zipSizeBytes did not converge");
    }

    private byte[] writeZipBytes(RuntimeTraceReplayExportManifest manifest, byte[] replayJsonBytes)
            throws IOException {
        byte[] manifestJson = objectMapper.writeValueAsBytes(manifest);
        ByteArrayOutputStream baos =
                new ByteArrayOutputStream(manifestJson.length + replayJsonBytes.length + 256);
        try (ZipOutputStream zos =
                        new ZipOutputStream(new BufferedOutputStream(baos), StandardCharsets.UTF_8)) {
            // STORED entries: total ZIP size tracks manifest payload length monotonically so
            // manifest.zipSizeBytes fixed-point iteration converges (DEFLATE is non-monotonic).
            putStoredEntry(zos, "manifest.json", manifestJson);
            putStoredEntry(zos, "replay.json", replayJsonBytes);
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

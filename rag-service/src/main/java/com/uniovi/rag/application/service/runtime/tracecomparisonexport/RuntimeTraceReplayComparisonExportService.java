package com.uniovi.rag.application.service.runtime.tracecomparisonexport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.uniovi.rag.application.service.runtime.tracecomparison.RuntimeTraceReplayComparisonService;
import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayComparisonOutcome;
import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayComparisonRequest;
import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayComparisonResult;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayMode;
import com.uniovi.rag.interfaces.rest.NotFoundException;
import org.springframework.stereotype.Service;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * P21: sole owner of replay-comparison ZIP export. Calls {@link RuntimeTraceReplayComparisonService#compare} only.
 */
@Service
public class RuntimeTraceReplayComparisonExportService {

    public static final long MAX_ZIP_SIZE_BYTES = 2097152L;
    public static final int EXPORT_SCHEMA_VERSION = 1;
    public static final String EXPORT_KIND = "REPLAY_COMPARISON";

    private final RuntimeTraceReplayComparisonService comparisonService;
    private final ObjectMapper objectMapper;
    private final long maxZipSizeBytes;

    public RuntimeTraceReplayComparisonExportService(RuntimeTraceReplayComparisonService comparisonService) {
        this(comparisonService, MAX_ZIP_SIZE_BYTES);
    }

    /**
     * Package-private: use {@link #MAX_ZIP_SIZE_BYTES} in production; tests pass a smaller cap to assert 413.
     */
    RuntimeTraceReplayComparisonExportService(
            RuntimeTraceReplayComparisonService comparisonService, long maxZipSizeBytes) {
        this.comparisonService = comparisonService;
        this.maxZipSizeBytes = maxZipSizeBytes;
        this.objectMapper =
                new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .disable(SerializationFeature.INDENT_OUTPUT)
                        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    public RuntimeTraceReplayComparisonExportArtifact exportByTraceId(UUID userId, UUID traceId) {
        RuntimeTraceReplayComparisonResult result =
                comparisonService.compare(RuntimeTraceReplayComparisonRequest.byTraceId(userId, traceId));
        throwIfNotExportable(result);
        return buildArtifact(
                result,
                "runtime-trace-replay-comparison_" + traceId + ".zip",
                RuntimeTraceReplayMode.BY_TRACE_ID,
                new ReplayComparisonExportManifest.Scope(traceId, null, null));
    }

    public RuntimeTraceReplayComparisonExportArtifact exportByMessageId(
            UUID userId, UUID conversationId, UUID messageId) {
        RuntimeTraceReplayComparisonResult result =
                comparisonService.compare(
                        RuntimeTraceReplayComparisonRequest.byMessageId(userId, conversationId, messageId));
        throwIfNotExportable(result);
        return buildArtifact(
                result,
                "runtime-trace-replay-comparison_message_" + messageId + ".zip",
                RuntimeTraceReplayMode.BY_MESSAGE_ID,
                new ReplayComparisonExportManifest.Scope(null, conversationId, messageId));
    }

    private static void throwIfNotExportable(RuntimeTraceReplayComparisonResult result) {
        if (result.runtimeTraceReplayComparisonOutcome()
                == RuntimeTraceReplayComparisonOutcome.ORIGINAL_TRACE_NOT_FOUND_OR_INACCESSIBLE) {
            throw new NotFoundException("trace not found");
        }
    }

    private RuntimeTraceReplayComparisonExportArtifact buildArtifact(
            RuntimeTraceReplayComparisonResult result,
            String filename,
            RuntimeTraceReplayMode selectorMode,
            ReplayComparisonExportManifest.Scope scope) {
        ReplayComparisonExportDocumentMapper.BoundedComparison bounded =
                ReplayComparisonExportDocumentMapper.toBoundedComparison(result);
        ReplayComparisonExportComparisonDocument comparisonDoc = bounded.document();
        int mismatchCount = comparisonDoc.mismatches().size();
        Instant generatedAt = Instant.now();
        ReplayComparisonExportManifest manifest =
                new ReplayComparisonExportManifest(
                        EXPORT_SCHEMA_VERSION,
                        EXPORT_KIND,
                        generatedAt,
                        result.userId(),
                        selectorMode.name(),
                        scope,
                        result.runtimeTraceReplayComparisonOutcome().name(),
                        result.replayOutcome().name(),
                        result.exactMatch(),
                        mismatchCount,
                        bounded.truncated());
        byte[] zipBytes;
        try {
            zipBytes = writeZipBytes(manifest, comparisonDoc);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to build replay-comparison export ZIP", e);
        }
        if (zipBytes.length > maxZipSizeBytes) {
            throw new RuntimeTraceReplayComparisonExportSizeExceededException(
                    "Replay-comparison export exceeds max ZIP size");
        }
        return new RuntimeTraceReplayComparisonExportArtifact(
                filename,
                RuntimeTraceReplayComparisonExportArtifact.MEDIA_TYPE_ZIP,
                zipBytes,
                zipBytes.length);
    }

    private byte[] writeZipBytes(
            ReplayComparisonExportManifest manifest, ReplayComparisonExportComparisonDocument comparisonDoc)
            throws IOException {
        byte[] manifestJson = objectMapper.writeValueAsBytes(manifest);
        byte[] comparisonJson = objectMapper.writeValueAsBytes(comparisonDoc);
        ByteArrayOutputStream baos = new ByteArrayOutputStream(manifestJson.length + comparisonJson.length + 256);
        try (ZipOutputStream zos =
                        new ZipOutputStream(new BufferedOutputStream(baos), StandardCharsets.UTF_8)) {
            ZipEntry m = new ZipEntry("manifest.json");
            zos.putNextEntry(m);
            zos.write(manifestJson);
            zos.closeEntry();
            ZipEntry c = new ZipEntry("comparison.json");
            zos.putNextEntry(c);
            zos.write(comparisonJson);
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
}

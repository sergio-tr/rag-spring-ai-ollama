package com.uniovi.rag.application.service.runtime.traceexport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.uniovi.rag.application.service.runtime.tracequery.RuntimeTraceQueryService;
import com.uniovi.rag.interfaces.rest.dto.RuntimeExecutionTraceDetailDto;
import com.uniovi.rag.interfaces.rest.dto.RuntimeExecutionTraceSummaryDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class RuntimeTraceExportService {

    public static final int MAX_TRACES_PER_CONVERSATION_EXPORT = 200;
    public static final long MAX_ZIP_SIZE_BYTES = 10L * 1024L * 1024L;
    public static final int EXPORT_SCHEMA_VERSION = 1;
    public static final String MEDIA_TYPE_ZIP = "application/zip";

    private static final DateTimeFormatter TRACE_CREATED_AT_FILENAME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssSSS'Z'").withZone(ZoneOffset.UTC);

    private final RuntimeTraceQueryService runtimeTraceQueryService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public RuntimeTraceExportService(RuntimeTraceQueryService runtimeTraceQueryService, ObjectMapper objectMapper) {
        this(runtimeTraceQueryService, objectMapper, Clock.systemUTC());
    }

    RuntimeTraceExportService(
            RuntimeTraceQueryService runtimeTraceQueryService,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.runtimeTraceQueryService = runtimeTraceQueryService;
        this.objectMapper = objectMapper.copy().disable(SerializationFeature.INDENT_OUTPUT)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        this.clock = clock;
    }

    public RuntimeTraceExportArtifact exportSingleTraceById(UUID userId, UUID traceId) {
        RuntimeExecutionTraceDetailDto detail = runtimeTraceQueryService.getTraceDetailById(userId, traceId);
        Instant now = Instant.now(clock);
        String filename = "runtime-trace_" + traceId + ".zip";
        return buildZipArtifact(
                new Manifest(
                        EXPORT_SCHEMA_VERSION,
                        ExportKind.SINGLE_TRACE.name(),
                        now,
                        userId,
                        new Scope(traceId, null, null, null),
                        new Counts(1, false, false),
                        List.of()),
                List.of(detail),
                filename,
                ExportKind.SINGLE_TRACE);
    }

    public RuntimeTraceExportArtifact exportSingleTraceByMessageId(UUID userId, UUID conversationId, UUID messageId) {
        RuntimeExecutionTraceDetailDto detail =
                runtimeTraceQueryService.getMostRecentTraceDetailByMessageId(userId, conversationId, messageId);
        Instant now = Instant.now(clock);
        String filename = "runtime-trace_message_" + messageId + ".zip";
        return buildZipArtifact(
                new Manifest(
                        EXPORT_SCHEMA_VERSION,
                        ExportKind.SINGLE_TRACE.name(),
                        now,
                        userId,
                        new Scope(null, conversationId, messageId, null),
                        new Counts(1, false, false),
                        List.of()),
                List.of(detail),
                filename,
                ExportKind.SINGLE_TRACE);
    }

    public RuntimeTraceExportArtifact exportConversationBundle(
            UUID userId,
            UUID conversationId,
            Optional<Instant> createdAtFrom,
            Optional<Instant> createdAtTo,
            Optional<String> workflowName
    ) {
        Instant now = Instant.now(clock);
        String filename = "runtime-traces_conversation_" + conversationId + ".zip";

        List<RuntimeExecutionTraceSummaryDto> summaries = new ArrayList<>(MAX_TRACES_PER_CONVERSATION_EXPORT);
        Page<RuntimeExecutionTraceSummaryDto> p0 =
                runtimeTraceQueryService.listConversationTraceSummaries(
                        userId, conversationId, createdAtFrom, createdAtTo, workflowName, 0, 100);
        summaries.addAll(p0.getContent());
        if (summaries.size() < MAX_TRACES_PER_CONVERSATION_EXPORT) {
            Page<RuntimeExecutionTraceSummaryDto> p1 =
                    runtimeTraceQueryService.listConversationTraceSummaries(
                            userId, conversationId, createdAtFrom, createdAtTo, workflowName, 1, 100);
            summaries.addAll(p1.getContent());
        }
        if (summaries.size() > MAX_TRACES_PER_CONVERSATION_EXPORT) {
            summaries = summaries.subList(0, MAX_TRACES_PER_CONVERSATION_EXPORT);
        }

        boolean capped = false;
        if (summaries.size() == MAX_TRACES_PER_CONVERSATION_EXPORT) {
            Page<RuntimeExecutionTraceSummaryDto> p2 =
                    runtimeTraceQueryService.listConversationTraceSummaries(
                            userId, conversationId, createdAtFrom, createdAtTo, workflowName, 2, 1);
            capped = !p2.getContent().isEmpty();
        }

        List<RuntimeExecutionTraceDetailDto> details = new ArrayList<>(summaries.size());
        for (RuntimeExecutionTraceSummaryDto s : summaries) {
            details.add(runtimeTraceQueryService.getTraceDetailById(userId, s.id()));
        }

        Counts counts = new Counts(details.size(), capped, capped);
        return buildZipArtifact(
                new Manifest(
                        EXPORT_SCHEMA_VERSION,
                        ExportKind.CONVERSATION_BUNDLE.name(),
                        now,
                        userId,
                        new Scope(null, conversationId, null, new Filters(createdAtFrom.orElse(null), createdAtTo.orElse(null), workflowName.orElse(null))),
                        counts,
                        List.of()),
                details,
                filename,
                ExportKind.CONVERSATION_BUNDLE);
    }

    private RuntimeTraceExportArtifact buildZipArtifact(
            Manifest manifestBase,
            List<RuntimeExecutionTraceDetailDto> traceDetails,
            String zipFilename,
            ExportKind exportKind
    ) {
        List<TraceSource> sources = new ArrayList<>(traceDetails.size());
        List<IndexRow> index = new ArrayList<>(traceDetails.size());
        List<TraceFile> files = new ArrayList<>(traceDetails.size());

        for (RuntimeExecutionTraceDetailDto d : traceDetails) {
            String createdAtUtcCompact = TRACE_CREATED_AT_FILENAME_FORMAT.format(d.createdAt());
            String perTraceName = createdAtUtcCompact + "_" + d.id() + ".json";
            String zipPath = "traces/" + perTraceName;
            sources.add(new TraceSource(d.id(), zipPath));
            index.add(new IndexRow(d.id(), d.createdAt(), zipPath, d.conversationId(), d.messageId(), d.workflowName(), d.schemaVersion()));
            files.add(new TraceFile(zipPath, new TraceExportRow(d)));
        }

        Manifest manifest =
                new Manifest(
                        manifestBase.schemaVersion(),
                        manifestBase.exportKind(),
                        manifestBase.generatedAt(),
                        manifestBase.requestedByUserId(),
                        manifestBase.scope(),
                        manifestBase.counts(),
                        sources);

        byte[] zipBytes;
        try {
            zipBytes = writeZipBytes(manifest, index, files);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate export ZIP", e);
        }

        if (zipBytes.length > MAX_ZIP_SIZE_BYTES) {
            throw new RuntimeTraceExportSizeLimitExceededException("Export exceeds max ZIP size");
        }

        return new RuntimeTraceExportArtifact(
                zipFilename,
                MEDIA_TYPE_ZIP,
                zipBytes,
                zipBytes.length,
                exportKind.name());
    }

    private byte[] writeZipBytes(Manifest manifest, List<IndexRow> index, List<TraceFile> traceFiles)
            throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos =
                     new ZipOutputStream(new BufferedOutputStream(baos), StandardCharsets.UTF_8)) {
            // Ensure deterministic ZIP bytes: fix entry timestamps to the manifest generation instant.
            long entryTimeMillis = manifest.generatedAt().toEpochMilli();
            putJson(zos, "manifest.json", manifest, entryTimeMillis);
            putJson(zos, "traces/index.json", new Index(index), entryTimeMillis);
            for (TraceFile f : traceFiles) {
                putJson(zos, f.zipPath(), f.payload(), entryTimeMillis);
            }
        }
        return baos.toByteArray();
    }

    private void putJson(ZipOutputStream zos, String name, Object value, long entryTimeMillis) throws IOException {
        ZipEntry e = new ZipEntry(name);
        e.setTime(entryTimeMillis);
        zos.putNextEntry(e);
        byte[] bytes = objectMapper.writeValueAsBytes(value);
        zos.write(bytes);
        zos.closeEntry();
    }

    enum ExportKind {
        SINGLE_TRACE,
        CONVERSATION_BUNDLE
    }

    record Manifest(
            int schemaVersion,
            String exportKind,
            Instant generatedAt,
            UUID requestedByUserId,
            Scope scope,
            Counts counts,
            List<TraceSource> sources
    ) {}

    record Scope(
            UUID traceId,
            UUID conversationId,
            UUID messageId,
            Filters filters
    ) {}

    record Filters(
            Instant createdAtFrom,
            Instant createdAtTo,
            String workflowName
    ) {}

    record Counts(
            int traceCount,
            boolean traceCountCapped,
            boolean truncated
    ) {}

    record TraceSource(
            UUID traceId,
            String filename
    ) {}

    record Index(
            List<IndexRow> items
    ) {}

    record IndexRow(
            UUID traceId,
            Instant createdAt,
            String filename,
            UUID conversationId,
            UUID messageId,
            String workflowName,
            int schemaVersion
    ) {}

    record TraceFile(
            String zipPath,
            TraceExportRow payload
    ) {}

    record TraceExportRow(
            UUID id,
            Instant createdAt,
            UUID userId,
            UUID projectId,
            UUID conversationId,
            UUID messageId,
            String correlationId,
            UUID resolvedConfigSnapshotId,
            String configHash,
            String workflowName,
            boolean memoryAttempted,
            String memoryOutcome,
            boolean routingAttempted,
            String routingOutcome,
            String routingRouteKind,
            boolean routingFallbackApplied,
            boolean routingWorkflowSelectorInvoked,
            String deterministicToolOutcome,
            String functionCallingOutcome,
            String advisorOutcome,
            boolean judgeAttempted,
            String judgeCandidateSource,
            String judgeFinalOutcome,
            boolean judgeFinalAnswerFromRetry,
            String clarificationOutcome,
            int schemaVersion,
            Object executionTraceJson,
            Object stagesJson
    ) {
        TraceExportRow(RuntimeExecutionTraceDetailDto d) {
            this(
                    d.id(),
                    d.createdAt(),
                    d.userId(),
                    d.projectId(),
                    d.conversationId(),
                    d.messageId(),
                    d.correlationId(),
                    d.resolvedConfigSnapshotId(),
                    d.configHash(),
                    d.workflowName(),
                    d.memoryAttempted(),
                    d.memoryOutcome(),
                    d.routingAttempted(),
                    d.routingOutcome(),
                    d.routingRouteKind(),
                    d.routingFallbackApplied(),
                    d.routingWorkflowSelectorInvoked(),
                    d.deterministicToolOutcome(),
                    d.functionCallingOutcome(),
                    d.advisorOutcome(),
                    d.judgeAttempted(),
                    d.judgeCandidateSource(),
                    d.judgeFinalOutcome(),
                    d.judgeFinalAnswerFromRetry(),
                    d.clarificationOutcome(),
                    d.schemaVersion(),
                    d.executionTraceJson(),
                    d.stagesJson());
        }
    }
}


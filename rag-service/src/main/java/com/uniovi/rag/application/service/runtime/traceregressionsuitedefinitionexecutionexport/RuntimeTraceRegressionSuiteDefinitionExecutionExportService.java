package com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionexecutionexport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.uniovi.rag.application.service.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteService;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionService;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteEntry;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteOutcome;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteRequest;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteResult;
import com.uniovi.rag.interfaces.rest.NotFoundException;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuite.RuntimeTraceRegressionSuiteResponseDto;
import org.springframework.beans.factory.annotation.Autowired;
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
 * P37: materialize saved definition once, optional P36-identical conversation guard on X2, {@code execute} once, then
 * two-entry ZIP ({@code manifest.json}, {@code suite.json}) with convergent {@code zipSizeBytes}.
 */
@Service
public class RuntimeTraceRegressionSuiteDefinitionExecutionExportService {

    public static final long MAX_ZIP_SIZE_BYTES = 2097152L;
    public static final int EXPORT_SCHEMA_VERSION = 1;
    public static final String EXPORT_KIND = "REGRESSION_SUITE_DEFINITION_EXECUTION";
    public static final String SELECTOR_SAVED_DEFINITION_BY_ID = "SAVED_DEFINITION_BY_ID";
    public static final String SELECTOR_SAVED_DEFINITION_BY_ID_WITH_P36_CONVERSATION_GUARD =
            "SAVED_DEFINITION_BY_ID_WITH_P36_CONVERSATION_GUARD";

    private final RuntimeTraceRegressionSuiteDefinitionService definitionService;
    private final RuntimeTraceRegressionSuiteService suiteService;
    private final ObjectMapper objectMapper;
    private final long maxZipSizeBytes;

    @Autowired
    public RuntimeTraceRegressionSuiteDefinitionExecutionExportService(
            RuntimeTraceRegressionSuiteDefinitionService definitionService,
            RuntimeTraceRegressionSuiteService suiteService) {
        this(definitionService, suiteService, MAX_ZIP_SIZE_BYTES);
    }

    RuntimeTraceRegressionSuiteDefinitionExecutionExportService(
            RuntimeTraceRegressionSuiteDefinitionService definitionService,
            RuntimeTraceRegressionSuiteService suiteService,
            long maxZipSizeBytes) {
        this.definitionService = definitionService;
        this.suiteService = suiteService;
        this.maxZipSizeBytes = maxZipSizeBytes;
        this.objectMapper =
                new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .disable(SerializationFeature.INDENT_OUTPUT)
                        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    /**
     * X1: saved definition by id only (no conversation guard).
     */
    public RuntimeTraceRegressionSuiteDefinitionExecutionExportArtifact exportByDefinitionId(UUID definitionId, UUID userId) {
        RuntimeTraceRegressionSuiteRequest req = definitionService.materializeToSuiteRequest(definitionId, userId);
        RuntimeTraceRegressionSuiteResult result = suiteService.execute(req);
        throwIfNotExportable(result);
        try {
            ObjectNode scope = objectMapper.createObjectNode();
            scope.put("definitionId", definitionId.toString());
            return buildArtifact(
                    userId,
                    SELECTOR_SAVED_DEFINITION_BY_ID,
                    scope,
                    result,
                    "runtime-trace-regression-suite-definition-execution_" + definitionId + ".zip");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to build definition execution export ZIP", e);
        }
    }

    /**
     * X2: saved definition with P36-identical conversation entry guard before {@code execute}.
     */
    public RuntimeTraceRegressionSuiteDefinitionExecutionExportArtifact exportByDefinitionIdAndConversation(
            UUID definitionId, UUID conversationId, UUID userId) {
        RuntimeTraceRegressionSuiteRequest req = definitionService.materializeToSuiteRequest(definitionId, userId);
        if (conversationScopedGuardFails(conversationId, req)) {
            throw new NotFoundException("P37_DEFINITION_EXECUTION_EXPORT_CONVERSATION_GUARD");
        }
        RuntimeTraceRegressionSuiteResult result = suiteService.execute(req);
        throwIfNotExportable(result);
        try {
            ObjectNode scope = objectMapper.createObjectNode();
            scope.put("definitionId", definitionId.toString());
            scope.put("conversationId", conversationId.toString());
            scope.put("p36ConversationEntryGuardApplied", true);
            return buildArtifact(
                    userId,
                    SELECTOR_SAVED_DEFINITION_BY_ID_WITH_P36_CONVERSATION_GUARD,
                    scope,
                    result,
                    "runtime-trace-regression-suite-definition-execution_"
                            + definitionId
                            + "_conversation_"
                            + conversationId
                            + ".zip");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to build definition execution export ZIP", e);
        }
    }

    private static void throwIfNotExportable(RuntimeTraceRegressionSuiteResult result) {
        if (result.suiteOutcome() == RuntimeTraceRegressionSuiteOutcome.NOT_ATTEMPTED) {
            throw new RuntimeTraceRegressionSuiteDefinitionExecutionExportNotAttemptedException(
                    "suite outcome is NOT_ATTEMPTED");
        }
    }

    private static boolean conversationScopedGuardFails(UUID pathConversationId, RuntimeTraceRegressionSuiteRequest request) {
        boolean sawConversation = false;
        for (RuntimeTraceRegressionSuiteEntry e : request.entries()) {
            if (e instanceof RuntimeTraceRegressionSuiteEntry.ByConversation bc) {
                sawConversation = true;
                if (!pathConversationId.equals(bc.conversationId())) {
                    return true;
                }
            }
        }
        return !sawConversation;
    }

    private RuntimeTraceRegressionSuiteDefinitionExecutionExportArtifact buildArtifact(
            UUID requestedByUserId,
            String selectorType,
            Object scope,
            RuntimeTraceRegressionSuiteResult result,
            String filename)
            throws IOException {
        byte[] suiteJsonBytes =
                objectMapper.writeValueAsBytes(RuntimeTraceRegressionSuiteResponseDto.fromResult(result));

        Instant generatedAt = Instant.now();
        String suiteOutcome = result.suiteOutcome().name();
        int requestedEntryCount = result.summary().requestedEntryCount();
        int processedEntryCount = result.summary().processedEntryCount();

        byte[] zipBytes =
                buildZipBytes(
                        generatedAt,
                        requestedByUserId,
                        selectorType,
                        scope,
                        suiteOutcome,
                        requestedEntryCount,
                        processedEntryCount,
                        suiteJsonBytes);

        if (zipBytes.length > maxZipSizeBytes) {
            throw new RuntimeTraceRegressionSuiteDefinitionExecutionExportSizeExceededException(
                    "definition execution export exceeds max ZIP size");
        }
        return new RuntimeTraceRegressionSuiteDefinitionExecutionExportArtifact(
                filename,
                RuntimeTraceRegressionSuiteDefinitionExecutionExportArtifact.MEDIA_TYPE_ZIP,
                zipBytes,
                zipBytes.length);
    }

    private byte[] buildZipBytes(
            Instant generatedAt,
            UUID requestedByUserId,
            String selectorType,
            Object scope,
            String suiteOutcome,
            int requestedEntryCount,
            int processedEntryCount,
            byte[] suiteJsonBytes)
            throws IOException {
        long candidateZipSizeBytes = 0L;
        for (int i = 0; i < 64; i++) {
            RuntimeTraceRegressionSuiteDefinitionExecutionExportManifest manifest =
                    new RuntimeTraceRegressionSuiteDefinitionExecutionExportManifest(
                            EXPORT_SCHEMA_VERSION,
                            EXPORT_KIND,
                            generatedAt,
                            requestedByUserId.toString(),
                            selectorType,
                            scope,
                            suiteOutcome,
                            requestedEntryCount,
                            processedEntryCount,
                            candidateZipSizeBytes,
                            false);
            byte[] zipBytes = writeZipToBytes(manifest, suiteJsonBytes);
            if (manifest.zipSizeBytes() == zipBytes.length) {
                return zipBytes;
            }
            candidateZipSizeBytes = zipBytes.length;
        }
        throw new IllegalStateException("P37 definition execution export manifest zipSizeBytes did not converge");
    }

    private byte[] writeZipToBytes(
            RuntimeTraceRegressionSuiteDefinitionExecutionExportManifest manifest, byte[] suiteJsonBytes)
            throws IOException {
        byte[] manifestJson = objectMapper.writeValueAsBytes(manifest);
        ByteArrayOutputStream baos =
                new ByteArrayOutputStream(manifestJson.length + suiteJsonBytes.length + 256);
        try (ZipOutputStream zos =
                        new ZipOutputStream(new BufferedOutputStream(baos), StandardCharsets.UTF_8)) {
            putStoredEntry(zos, "manifest.json", manifestJson);
            putStoredEntry(zos, "suite.json", suiteJsonBytes);
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

package com.uniovi.rag.application.service.runtime.traceregressionsuiteexport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.uniovi.rag.application.service.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteService;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteOutcome;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteRequest;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteResult;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuite.RuntimeTraceRegressionSuiteConversationExecuteRequestDto;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuite.RuntimeTraceRegressionSuiteExecuteRequestDto;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuite.RuntimeTraceRegressionSuiteResponseDto;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * P32: one {@link RuntimeTraceRegressionSuiteService#execute} per export; {@code suite.json} from {@link
 * RuntimeTraceRegressionSuiteResponseDto#fromResult} only.
 */
@Service
public class RuntimeTraceRegressionSuiteExportService {

    public static final long MAX_ZIP_SIZE_BYTES = 2097152L;
    public static final int EXPORT_SCHEMA_VERSION = 1;
    public static final String EXPORT_KIND = "REGRESSION_SUITE";
    public static final String SELECTOR_EXPLICIT_SUITE = "EXPLICIT_SUITE";
    public static final String SELECTOR_CONVERSATION_SCOPED_SUITE = "CONVERSATION_SCOPED_SUITE";

    private final RuntimeTraceRegressionSuiteService suiteService;
    private final ObjectMapper objectMapper;
    private final long maxZipSizeBytes;

    @Autowired
    public RuntimeTraceRegressionSuiteExportService(RuntimeTraceRegressionSuiteService suiteService) {
        this(suiteService, MAX_ZIP_SIZE_BYTES);
    }

    RuntimeTraceRegressionSuiteExportService(RuntimeTraceRegressionSuiteService suiteService, long maxZipSizeBytes) {
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
     * Route 1: explicit suite entries; {@code scope} in manifest is the accepted {@link RuntimeTraceRegressionSuiteExecuteRequestDto}.
     */
    public RuntimeTraceRegressionSuiteExportArtifact exportExplicit(
            UUID userId,
            RuntimeTraceRegressionSuiteRequest domainRequest,
            RuntimeTraceRegressionSuiteExecuteRequestDto acceptedBody) {
        RuntimeTraceRegressionSuiteResult result = suiteService.execute(domainRequest);
        throwIfNotExportable(result);
        try {
            return buildArtifact(
                    userId,
                    SELECTOR_EXPLICIT_SUITE,
                    acceptedBody,
                    result,
                    "runtime-trace-regression-suite.zip");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to build regression suite export ZIP", e);
        }
    }

    /**
     * Route 2: conversation path + batch specs; {@code scope} is path id plus accepted body.
     */
    public RuntimeTraceRegressionSuiteExportArtifact exportConversationScoped(
            UUID userId,
            RuntimeTraceRegressionSuiteRequest domainRequest,
            UUID pathConversationId,
            RuntimeTraceRegressionSuiteConversationExecuteRequestDto acceptedBody) {
        RuntimeTraceRegressionSuiteResult result = suiteService.execute(domainRequest);
        throwIfNotExportable(result);
        Object scope = new ConversationScopedSuiteManifestScope(pathConversationId, acceptedBody);
        try {
            return buildArtifact(
                    userId,
                    SELECTOR_CONVERSATION_SCOPED_SUITE,
                    scope,
                    result,
                    "runtime-trace-regression-suite_conversation_" + pathConversationId + ".zip");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to build regression suite export ZIP", e);
        }
    }

    private static void throwIfNotExportable(RuntimeTraceRegressionSuiteResult result) {
        if (result.suiteOutcome() == RuntimeTraceRegressionSuiteOutcome.NOT_ATTEMPTED) {
            throw new RuntimeTraceRegressionSuiteExportNotAttemptedException("suite outcome is NOT_ATTEMPTED");
        }
    }

    private RuntimeTraceRegressionSuiteExportArtifact buildArtifact(
            UUID requestedByUserId,
            String selectorType,
            Object scope,
            RuntimeTraceRegressionSuiteResult result,
            String filename)
            throws IOException {
        RuntimeTraceRegressionSuiteResponseDto suiteDto = RuntimeTraceRegressionSuiteResponseDto.fromResult(result);
        byte[] suiteJsonBytes = objectMapper.writeValueAsBytes(suiteDto);

        Instant generatedAt = Instant.now();
        String suiteOutcome = result.suiteOutcome().name();
        int requestedEntryCount = result.summary().requestedEntryCount();
        int processedEntryCount = result.summary().processedEntryCount();

        byte[] zipBytes =
                buildZipWithStableManifestSize(
                        generatedAt,
                        requestedByUserId,
                        selectorType,
                        scope,
                        suiteOutcome,
                        requestedEntryCount,
                        processedEntryCount,
                        suiteJsonBytes);

        if (zipBytes.length > maxZipSizeBytes) {
            throw new RuntimeTraceRegressionSuiteExportSizeExceededException("Regression suite export exceeds max ZIP size");
        }
        return new RuntimeTraceRegressionSuiteExportArtifact(
                filename, RuntimeTraceRegressionSuiteExportArtifact.MEDIA_TYPE_ZIP, zipBytes, zipBytes.length);
    }

    private byte[] buildZipWithStableManifestSize(
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
            RegressionSuiteExportManifest manifest =
                    new RegressionSuiteExportManifest(
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
            byte[] zipBytes = writeZipBytes(manifest, suiteJsonBytes);
            if (manifest.zipSizeBytes() == zipBytes.length) {
                return zipBytes;
            }
            candidateZipSizeBytes = zipBytes.length;
        }
        throw new IllegalStateException("Regression suite export manifest zipSizeBytes did not converge");
    }

    private byte[] writeZipBytes(RegressionSuiteExportManifest manifest, byte[] suiteJsonBytes) throws IOException {
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
        CRC32 crc = new CRC32();
        crc.update(data);
        entry.setCrc(crc.getValue());
        zos.putNextEntry(entry);
        zos.write(data);
        zos.closeEntry();
    }
}

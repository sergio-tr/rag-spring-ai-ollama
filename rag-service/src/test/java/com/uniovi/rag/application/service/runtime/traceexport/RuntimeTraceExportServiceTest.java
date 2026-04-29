package com.uniovi.rag.application.service.runtime.traceexport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.service.runtime.tracequery.RuntimeTraceQueryService;
import com.uniovi.rag.interfaces.rest.dto.RuntimeExecutionTraceDetailDto;
import com.uniovi.rag.interfaces.rest.dto.RuntimeExecutionTraceSummaryDto;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RuntimeTraceExportServiceTest {

    @Test
    void exportSingleTraceByMessageId_resolvesMostRecentThroughQueryService() {
        RuntimeTraceQueryService query = mock(RuntimeTraceQueryService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-04-10T12:00:00Z"), ZoneOffset.UTC);
        RuntimeTraceExportService svc =
                new RuntimeTraceExportService(query, new ObjectMapper().findAndRegisterModules(), clock);

        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID traceId = UUID.randomUUID();

        when(query.getMostRecentTraceDetailByMessageId(eq(userId), eq(conversationId), eq(messageId)))
                .thenReturn(minDetail(userId, conversationId, messageId, traceId, Instant.parse("2026-04-10T11:59:59Z")));

        RuntimeTraceExportArtifact artifact = svc.exportSingleTraceByMessageId(userId, conversationId, messageId);

        assertThat(artifact.mediaType()).isEqualTo("application/zip");
        verify(query).getMostRecentTraceDetailByMessageId(eq(userId), eq(conversationId), eq(messageId));
        verifyNoMoreInteractions(query);
    }

    @Test
    void exportSingleTraceById_isDeterministicForSameInputsAndFixedClock() {
        RuntimeTraceQueryService query = mock(RuntimeTraceQueryService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-04-10T12:00:00Z"), ZoneOffset.UTC);
        RuntimeTraceExportService svc =
                new RuntimeTraceExportService(query, new ObjectMapper().findAndRegisterModules(), clock);

        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID traceId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-04-10T11:59:59.123Z");

        RuntimeExecutionTraceDetailDto detail = minDetail(userId, conversationId, messageId, traceId, createdAt);
        when(query.getTraceDetailById(eq(userId), eq(traceId))).thenReturn(detail);

        RuntimeTraceExportArtifact a1 = svc.exportSingleTraceById(userId, traceId);
        RuntimeTraceExportArtifact a2 = svc.exportSingleTraceById(userId, traceId);

        assertThat(a1.content()).isEqualTo(a2.content());

        List<String> entries = zipEntriesInOrder(a1.content());
        assertThat(entries).startsWith("manifest.json", "traces/index.json");
        assertThat(entries).containsExactly(
                "manifest.json",
                "traces/index.json",
                "traces/20260410T115959123Z_" + traceId + ".json");
    }

    @Test
    void exportConversationBundle_appliesCountCapAndSetsManifestFlags() throws Exception {
        RuntimeTraceQueryService query = mock(RuntimeTraceQueryService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-04-10T12:00:00Z"), ZoneOffset.UTC);
        RuntimeTraceExportService svc =
                new RuntimeTraceExportService(query, new ObjectMapper().findAndRegisterModules(), clock);

        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        List<RuntimeExecutionTraceSummaryDto> p0 = new ArrayList<>();
        List<RuntimeExecutionTraceSummaryDto> p1 = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            p0.add(summary(UUID.randomUUID(), userId, conversationId));
            p1.add(summary(UUID.randomUUID(), userId, conversationId));
        }
        Page<RuntimeExecutionTraceSummaryDto> page0 = new PageImpl<>(p0);
        Page<RuntimeExecutionTraceSummaryDto> page1 = new PageImpl<>(p1);
        Page<RuntimeExecutionTraceSummaryDto> page2HasMore = new PageImpl<>(List.of(summary(UUID.randomUUID(), userId, conversationId)));

        when(query.listConversationTraceSummaries(eq(userId), eq(conversationId), any(), any(), any(), eq(0), eq(100)))
                .thenReturn(page0);
        when(query.listConversationTraceSummaries(eq(userId), eq(conversationId), any(), any(), any(), eq(1), eq(100)))
                .thenReturn(page1);
        when(query.listConversationTraceSummaries(eq(userId), eq(conversationId), any(), any(), any(), eq(2), eq(1)))
                .thenReturn(page2HasMore);

        when(query.getTraceDetailById(eq(userId), any(UUID.class))).thenAnswer(inv -> {
            UUID traceId = inv.getArgument(1);
            return minDetail(userId, conversationId, null, traceId, Instant.parse("2026-04-10T11:00:00Z"));
        });

        RuntimeTraceExportArtifact artifact =
                svc.exportConversationBundle(userId, conversationId, Optional.empty(), Optional.empty(), Optional.empty());

        String manifestJson = readZipEntryUtf8(artifact.content(), "manifest.json");
        assertThat(manifestJson).contains("\"traceCount\":200");
        assertThat(manifestJson).contains("\"traceCountCapped\":true");
        assertThat(manifestJson).contains("\"truncated\":true");

        List<String> entries = zipEntriesInOrder(artifact.content());
        assertThat(entries.get(0)).isEqualTo("manifest.json");
        assertThat(entries.get(1)).isEqualTo("traces/index.json");
        assertThat(entries).hasSize(2 + 200);
    }

    @Test
    void exportSingleTraceById_sizeOverflowThrows() {
        RuntimeTraceQueryService query = mock(RuntimeTraceQueryService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-04-10T12:00:00Z"), ZoneOffset.UTC);
        RuntimeTraceExportService svc =
                new RuntimeTraceExportService(query, new ObjectMapper().findAndRegisterModules(), clock);

        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID traceId = UUID.randomUUID();

        RuntimeExecutionTraceDetailDto detail =
                minDetailWithLargeBlob(userId, conversationId, traceId, Instant.parse("2026-04-10T11:59:59Z"));
        when(query.getTraceDetailById(eq(userId), eq(traceId))).thenReturn(detail);

        assertThatThrownBy(() -> svc.exportSingleTraceById(userId, traceId))
                .isInstanceOf(RuntimeTraceExportSizeLimitExceededException.class);
    }

    private static RuntimeExecutionTraceSummaryDto summary(UUID traceId, UUID userId, UUID conversationId) {
        return new RuntimeExecutionTraceSummaryDto(
                traceId,
                Instant.parse("2026-04-10T11:00:00Z"),
                userId,
                UUID.randomUUID(),
                conversationId,
                UUID.randomUUID(),
                "corr",
                null,
                null,
                "wf",
                false,
                "",
                false,
                "",
                "",
                false,
                false,
                "",
                "",
                "",
                false,
                "",
                "",
                false,
                "");
    }

    private static RuntimeExecutionTraceDetailDto minDetail(
            UUID userId, UUID conversationId, UUID messageId, UUID traceId, Instant createdAt) {
        return new RuntimeExecutionTraceDetailDto(
                traceId,
                createdAt,
                userId,
                UUID.randomUUID(),
                conversationId,
                messageId,
                "corr",
                null,
                null,
                "wf",
                false,
                "",
                false,
                "",
                "",
                false,
                false,
                "",
                "",
                "",
                false,
                "",
                "",
                false,
                "",
                1,
                Map.of("k", "v"),
                List.of(Map.of("a", 1)));
    }

    private static RuntimeExecutionTraceDetailDto minDetailWithLargeBlob(
            UUID userId, UUID conversationId, UUID traceId, Instant createdAt) {
        // Use base64 of random bytes to reduce compressibility of the ZIP.
        byte[] bytes = new byte[12 * 1024 * 1024];
        new Random(123).nextBytes(bytes);
        String blob = Base64.getEncoder().encodeToString(bytes);
        return new RuntimeExecutionTraceDetailDto(
                traceId,
                createdAt,
                userId,
                UUID.randomUUID(),
                conversationId,
                null,
                "corr",
                null,
                null,
                "wf",
                false,
                "",
                false,
                "",
                "",
                false,
                false,
                "",
                "",
                "",
                false,
                "",
                "",
                false,
                "",
                1,
                Map.of("blob", blob),
                List.of());
    }

    private static List<String> zipEntriesInOrder(byte[] zipBytes) throws RuntimeException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            List<String> names = new ArrayList<>();
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                names.add(e.getName());
            }
            return names;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String readZipEntryUtf8(byte[] zipBytes, String name) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (name.equals(e.getName())) {
                    return new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new IllegalStateException("missing zip entry: " + name);
    }
}


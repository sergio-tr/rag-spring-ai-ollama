package com.uniovi.rag.tool.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.service.runtime.document.extraction.DocumentContentExtractor;
import com.uniovi.rag.application.service.runtime.retrieval.ContextRetriever;
import com.uniovi.rag.domain.runtime.RagSnapshotContextHolder;
import com.uniovi.rag.testsupport.ChatClientTestSupport;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

/** Unit tests for canonical meeting projection in {@link AbstractMetadataTool}. */
class CanonicalMeetingDedupeTest {

    private MetadataCountDocumentsTool tool;

    @BeforeEach
    void setUp() {
        ChatClient chatClient = ChatClientTestSupport.mockForUserPromptChain();
        ContextRetriever retriever = mock(ContextRetriever.class);
        DocumentContentExtractor extractor = mock(DocumentContentExtractor.class);
        MetadataLlmResponseCacheService llmCache = mock(MetadataLlmResponseCacheService.class);
        when(llmCache.getCachedResponse(anyString())).thenReturn("");
        when(llmCache.getCachedResponse(anyString(), anyString())).thenReturn("NONE");
        tool = new MetadataCountDocumentsTool(chatClient, retriever, extractor, llmCache);
    }

    @Test
    void filterScopedCorpusDocuments_excludesSupersededSnapshotWhenActivePresent() {
        String active = "4ec7f3a7-e0ba-4729-ad5e-a17102058d84";
        String stale = "822157f6-6ef7-4430-aa55-0c9435d9ab20";
        Document activePdf =
                doc(
                        "active",
                        Map.of(
                                "filename",
                                "ACTA 1.pdf",
                                "date_iso",
                                "2025-02-24",
                                "indexSnapshotId",
                                active,
                                "projectDocumentId",
                                "pdoc-1"));
        Document stalePdf =
                doc(
                        "stale",
                        Map.of(
                                "filename",
                                "ACTA 1.pdf",
                                "date_iso",
                                "2025-02-24",
                                "indexSnapshotId",
                                stale,
                                "projectDocumentId",
                                "pdoc-1-old"));

        List<Document> scoped = tool.filterScopedCorpusDocuments(List.of(activePdf, stalePdf));

        assertThat(scoped).containsExactly(activePdf);
    }

    @Test
    void filterScopedCorpusDocuments_excludesNonCanonicalPdfFilename() {
        Document pdf =
                doc(
                        "pdf",
                        Map.of(
                                "filename",
                                "ACTA 1.pdf",
                                "date_iso",
                                "2025-02-24",
                                "projectDocumentId",
                                "pdoc-1"));
        Document other =
                doc(
                        "other",
                        Map.of(
                                "filename",
                                "minutes-draft.docx",
                                "date_iso",
                                "2025-02-24",
                                "projectDocumentId",
                                "other"));

        List<Document> scoped = tool.filterScopedCorpusDocuments(List.of(pdf, other));

        assertThat(scoped).containsExactly(pdf);
    }

    @Test
    void resolveDominantActiveSnapshotIds_picksHighestCardinalitySnapshot() {
        String active = "snap-active";
        String stale = "snap-stale";
        List<Document> docs =
                List.of(
                        doc("a1", Map.of("filename", "ACTA 1.pdf", "indexSnapshotId", active)),
                        doc("a2", Map.of("filename", "ACTA 2.pdf", "indexSnapshotId", active)),
                        doc("s1", Map.of("filename", "ACTA 1.pdf", "indexSnapshotId", stale)));

        assertThat(tool.resolveDominantActiveSnapshotIds(docs)).containsExactly(active);
    }

    @Test
    void resolveDominantActiveSnapshotIds_prefersSnapshotCoveringMoreActasOverChunkCount() {
        String active = "snap-active-five-actas";
        String stale = "snap-stale-many-chunks";
        List<Document> docs = new java.util.ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            docs.add(
                    doc(
                            "active-" + i,
                            Map.of(
                                    "filename",
                                    "ACTA " + i + ".pdf",
                                    "indexSnapshotId",
                                    active,
                                    "projectDocumentId",
                                    "pdoc-" + i)));
        }
        for (int i = 0; i < 12; i++) {
            docs.add(
                    doc(
                            "stale-chunk-" + i,
                            Map.of(
                                    "filename",
                                    "ACTA 1.pdf",
                                    "indexSnapshotId",
                                    stale,
                                    "projectDocumentId",
                                    "pdoc-stale-1",
                                    "chunkIndex",
                                    i)));
        }

        assertThat(tool.resolveDominantActiveSnapshotIds(docs)).containsExactly(active);
    }

    @Test
    void isTopicActaListQuery_matchesRewrittenListarActasQuery() {
        assertThat(
                        StructuredMinuteMetadataSupport.isTopicActaListQuery(
                                "listar las actas que mencionan problemas del ascensor"))
                .isTrue();
    }

    @Test
    void resolveDominantActiveSnapshotIds_usesExecutionSnapshotWhenBound() {
        String stale = "snap-stale-many-chunks";
        try {
            RagSnapshotContextHolder.set(List.of(java.util.UUID.fromString("4ec7f3a7-e0ba-4729-ad5e-a17102058d84")));
            List<Document> docs =
                    List.of(
                            doc(
                                    "stale-chunk",
                                    Map.of(
                                            "filename",
                                            "ACTA 1.pdf",
                                            "indexSnapshotId",
                                            stale,
                                            "projectDocumentId",
                                            "pdoc-stale")));
            assertThat(tool.resolveDominantActiveSnapshotIds(docs))
                    .containsExactly("4ec7f3a7-e0ba-4729-ad5e-a17102058d84");
        } finally {
            RagSnapshotContextHolder.clear();
        }
    }

    @Test
    void filterScopedCorpusDocuments_excludesLegacyTxt() {
        Document pdf =
                doc(
                        "pdf",
                        Map.of(
                                "filename",
                                "ACTA 1.pdf",
                                "date_iso",
                                "2025-02-24",
                                "projectDocumentId",
                                "pdoc-1"));
        Document txt =
                doc(
                        "txt",
                        Map.of(
                                "filename",
                                "acta-24-02-2025.txt",
                                "date_iso",
                                "2025-02-24",
                                "projectDocumentId",
                                "legacy"));

        List<Document> scoped = tool.filterScopedCorpusDocuments(List.of(pdf, txt));

        assertThat(scoped).containsExactly(pdf);
    }

    @Test
    void canonicalizeMeetingsFromDocuments_hybridSectionAndDocLevelCountAsOne() {
        Map<String, Object> shared = new LinkedHashMap<>();
        shared.put("filename", "ACTA 5.pdf");
        shared.put("date", "2026-02-25");
        shared.put("date_iso", "2026-02-25");
        shared.put("startTime", "19:00");
        shared.put("projectDocumentId", "pdoc-5");
        shared.put("summary", "Reunión de prueba");
        List<Document> rows =
                List.of(
                        doc("chunk-0", withChunkIndex(shared, 0)),
                        doc("chunk-1", withChunkIndex(shared, 1)),
                        doc("chunk-doc", withChunkIndex(shared, 7)));

        var minutes = tool.canonicalizeMeetingsFromDocuments(rows);

        assertThat(minutes).hasSize(1);
        assertThat(minutes.get(0).filename()).isEqualTo("ACTA 5.pdf");
        assertThat(minutes.get(0).startTime()).contains("19:00");
    }

    @Test
    void actaDedupeKey_prefersProjectDocumentIdOverChunkHash() {
        Document a = doc("a", Map.of("projectDocumentId", "same", "document_id", "hash-a", "date_iso", "2025-02-24"));
        Document b = doc("b", Map.of("projectDocumentId", "same", "document_id", "hash-b", "date_iso", "2025-02-24"));

        assertThat(tool.actaDedupeKey(a)).isEqualTo(tool.actaDedupeKey(b));
    }

    private static Map<String, Object> withChunkIndex(Map<String, Object> base, int chunkIndex) {
        Map<String, Object> copy = new LinkedHashMap<>(base);
        copy.put("chunkIndex", chunkIndex);
        return copy;
    }

    private static Document doc(String id, Map<String, Object> meta) {
        Map<String, Object> chunkMeta = new LinkedHashMap<>(meta);
        if (!chunkMeta.containsKey("document_id")) {
            chunkMeta.put("document_id", id);
        }
        return new Document("body-" + id, chunkMeta);
    }
}

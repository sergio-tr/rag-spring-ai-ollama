package com.uniovi.rag.application.service.runtime.retrieval;

import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RetrievalPromptTextBuilderTest {

    @Test
    void build_whenNoCandidates_returnsEmpty() {
        RetrievalPromptTextBuilder b = new RetrievalPromptTextBuilder(mock(PgVectorStore.class), mock(ChatClient.class), 10, 0.7, false);
        assertThat(b.build(List.of(), "q", RetrievalLayout.CHUNK_SEPARATE)).isEmpty();
    }

    @Test
    void build_formatsMetadataPrefix_andSkipsBlankBlocks_andJoinsWithNewlines() {
        RetrievalPromptTextBuilder b = new RetrievalPromptTextBuilder(mock(PgVectorStore.class), mock(ChatClient.class), 10, 0.7, false);

        UUID snap = UUID.randomUUID();
        RetrievalCandidate c1 =
                new RetrievalCandidate(
                        "c1",
                        "Hello",
                        Map.of("date_iso", "2026-01-01", "president", "Ada", "topics", List.of("t1", "t2")),
                        0,
                        0,
                        0,
                        0,
                        snap,
                        0);
        RetrievalCandidate c2 =
                new RetrievalCandidate(
                        "c2",
                        "   ",
                        Map.of(),
                        0,
                        0,
                        0,
                        0,
                        snap,
                        0);

        String out = b.build(List.of(c1, c2), "q", RetrievalLayout.CHUNK_SEPARATE);

        assertThat(out)
                .contains("Acta: 2026-01-01.")
                .contains("Presidente: Ada.")
                .contains("Temas: t1, t2.")
                .contains("Contenido: Hello")
                .doesNotContain("c2");
    }

    @Test
    void build_whenDocumentCombined_groupsChunksByDocumentId_andCombinesContent() {
        RetrievalPromptTextBuilder b = new RetrievalPromptTextBuilder(mock(PgVectorStore.class), mock(ChatClient.class), 10, 0.7, false);

        UUID snap = UUID.randomUUID();
        Map<String, Object> baseMeta = Map.of("document_id", "d1", "date_iso", "2026-01-01");

        RetrievalCandidate c1 =
                new RetrievalCandidate(
                        "c1",
                        "Part A",
                        Map.of(
                                "document_id", "d1",
                                "chunk_index", 1,
                                "total_chunks", 2,
                                "date_iso", "2026-01-01"),
                        0,
                        0,
                        0,
                        0,
                        snap,
                        0);
        RetrievalCandidate c2 =
                new RetrievalCandidate(
                        "c2",
                        "Part B",
                        Map.of(
                                "document_id", "d1",
                                "chunk_index", 2,
                                "total_chunks", 2,
                                "date_iso", "2026-01-01"),
                        0,
                        0,
                        0,
                        0,
                        snap,
                        0);

        String out = b.build(List.of(c1, c2), "q", RetrievalLayout.DOCUMENT_COMBINED);

        assertThat(out).contains("Acta: 2026-01-01.").contains("Contenido: ");
        assertThat(out).contains("Part A").contains("Part B");
        assertThat(out).contains("\n\n"); // combined chunk separator
    }
}


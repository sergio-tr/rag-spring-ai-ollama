package com.uniovi.rag.application.service.knowledge.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.knowledge.CorpusScope;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KnowledgeChunkMetadataFactoryTest {

    @Test
    void buildV2_includesSnapshotAndProjectBindingKeys() {
        UUID documentId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();

        Map<String, Object> meta =
                KnowledgeChunkMetadataFactory.buildV2(
                        CorpusScope.PROJECT_SHARED,
                        documentId,
                        projectId,
                        null,
                        snapshotId,
                        "abc123",
                        "acta.pdf",
                        2,
                        5,
                        "hash-1");

        assertThat(meta)
                .containsEntry("indexSnapshotId", snapshotId.toString())
                .containsEntry("projectId", projectId.toString())
                .containsEntry("projectDocumentId", documentId.toString())
                .containsEntry("documentId", documentId.toString())
                .containsEntry("filename", "acta.pdf")
                .containsEntry("chunkIndex", 2)
                .containsEntry("corpusScope", "PROJECT_SHARED");
    }

    @Test
    void mergeActaStructuredFields_copiesScalarAndListFields() {
        Map<String, Object> acta = Map.of(
                "date_iso", "2026-02-25",
                "president", "Jorge Moreno Navarro",
                "numberOfAttendees", 17,
                "attendees", List.of("Jorge Moreno Navarro", "Ana Sánchez Herrera"),
                "topics", List.of("convivencia", "presupuesto"),
                "fieldPresence", Map.of("president", true, "date_iso", true));

        Map<String, Object> chunk = new HashMap<>();
        chunk.put("chunkIndex", 3);
        KnowledgeChunkMetadataFactory.mergeActaStructuredFields(chunk, acta);

        assertThat(chunk)
                .containsEntry("date_iso", "2026-02-25")
                .containsEntry("president", "Jorge Moreno Navarro")
                .containsEntry("numberOfAttendees", 17)
                .containsEntry("fieldPresence", acta.get("fieldPresence"));
        assertThat(chunk.get("attendees")).isInstanceOf(List.class);
        assertThat(chunk.get("topics")).isInstanceOf(List.class);
    }

    @Test
    void mergeSectionFields_setsActaDateDocumentTitleAndSectionType() {
        Map<String, Object> acta = Map.of("date_iso", "2026-02-25", "president", "Jorge Moreno Navarro");
        Map<String, Object> chunk = new HashMap<>();
        ActaSectionChunk section = new ActaSectionChunk("participants list", ActaSectionChunk.SECTION_PARTICIPANTS, 0);

        KnowledgeChunkMetadataFactory.mergeSectionFields(chunk, section, acta, "ACTA 5.pdf");

        assertThat(chunk)
                .containsEntry("sectionType", ActaSectionChunk.SECTION_PARTICIPANTS)
                .containsEntry("documentTitle", "ACTA 5.pdf")
                .containsEntry("actaDate", "2026-02-25")
                .containsEntry("president", "Jorge Moreno Navarro");
    }

    @Test
    void buildActaEmbeddingPrefix_includesDateAndPresident() {
        Map<String, Object> acta = Map.of(
                "date_iso", "2025-02-24",
                "president", "Juan Pérez Gutiérrez");

        String prefix = KnowledgeChunkMetadataFactory.buildActaEmbeddingPrefix(acta);

        assertThat(prefix).contains("2025-02-24").contains("Juan Pérez Gutiérrez");
    }
}

package com.uniovi.rag.application.service.knowledge.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.knowledge.CorpusScope;
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
}

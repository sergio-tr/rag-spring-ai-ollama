package com.uniovi.rag.domain.knowledge;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceDocumentTest {

    @Test
    void recordConstruction() {
        UUID id = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        WorkspaceDocument doc = new WorkspaceDocument(
                id,
                projectId,
                CorpusScope.CHAT_LOCAL,
                conversationId,
                "checksum",
                "uri",
                "text/plain",
                42L,
                snapshotId,
                true);
        assertThat(doc.id()).isEqualTo(id);
        assertThat(doc.projectId()).isEqualTo(projectId);
        assertThat(doc.corpusScope()).isEqualTo(CorpusScope.CHAT_LOCAL);
        assertThat(doc.conversationId()).isEqualTo(conversationId);
        assertThat(doc.contentChecksum()).isEqualTo("checksum");
        assertThat(doc.storageUri()).isEqualTo("uri");
        assertThat(doc.mimeType()).isEqualTo("text/plain");
        assertThat(doc.byteSize()).isEqualTo(42L);
        assertThat(doc.currentIndexSnapshotId()).isEqualTo(snapshotId);
        assertThat(doc.requiresReindex()).isTrue();
    }
}

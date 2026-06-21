package com.uniovi.rag.application.service.runtime.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class RetrievalCandidateIdsTest {

    @Test
    void fromDocument_readsCamelCaseChunkIndex() {
        UUID sid = UUID.randomUUID();
        Document doc =
                new Document(
                        "t",
                        Map.of(
                                "documentId",
                                UUID.randomUUID().toString(),
                                "chunkIndex",
                                3));

        String id = RetrievalCandidateIds.fromDocument(doc, sid);

        assertThat(id).endsWith(":3");
    }
}

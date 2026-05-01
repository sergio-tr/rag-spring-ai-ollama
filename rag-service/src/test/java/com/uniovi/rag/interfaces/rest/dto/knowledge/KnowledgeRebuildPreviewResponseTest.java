package com.uniovi.rag.interfaces.rest.dto.knowledge;

import com.uniovi.rag.application.service.knowledge.KnowledgeRebuildPreviewResult;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.domain.knowledge.KnowledgeBuildProjection;
import com.uniovi.rag.domain.knowledge.KnowledgeReindexDecision;
import com.uniovi.rag.domain.knowledge.KnowledgeReindexKind;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeRebuildPreviewResponseTest {

    @Test
    void from_mapsProjectionAndDecision() {
        KnowledgeBuildProjection projection = new KnowledgeBuildProjection(
                2,
                MaterializationStrategy.DOCUMENT_LEVEL,
                512,
                64,
                "emb-id",
                true,
                ReindexImpact.none(),
                null,
                "hash9");
        KnowledgeRebuildPreviewResult result =
                new KnowledgeRebuildPreviewResult(projection, new KnowledgeReindexDecision(KnowledgeReindexKind.HARD_REBUILD));
        UUID conversationId = UUID.randomUUID();

        KnowledgeRebuildPreviewResponse resp =
                KnowledgeRebuildPreviewResponse.from(result, CorpusScope.PROJECT_SHARED, conversationId);

        assertThat(resp.projectionVersion()).isEqualTo(2);
        assertThat(resp.materializationStrategy()).isEqualTo(MaterializationStrategy.DOCUMENT_LEVEL);
        assertThat(resp.chunkMaxChars()).isEqualTo(512);
        assertThat(resp.chunkOverlap()).isEqualTo(64);
        assertThat(resp.embeddingModelId()).isEqualTo("emb-id");
        assertThat(resp.metadataExtractionEnabled()).isTrue();
        assertThat(resp.reindexDecision()).isEqualTo(KnowledgeReindexKind.HARD_REBUILD);
        assertThat(resp.configHash()).isEqualTo("hash9");
        assertThat(resp.corpusScope()).isEqualTo(CorpusScope.PROJECT_SHARED);
        assertThat(resp.conversationId()).isEqualTo(conversationId);
    }
}

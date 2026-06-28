package com.uniovi.rag.infrastructure.vector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;

class PgVectorStoreRegistryTest {

    private static final String EMBEDDING_MODEL_ID = "mxbai-embed-large";

    @Test
    void forEmbeddingModelIdRejectsBlankIdsWithoutBuildingStores() {
        ProviderAwareEmbeddingModelFactory factory = mock(ProviderAwareEmbeddingModelFactory.class);
        PgVectorStoreRegistry registry = new PgVectorStoreRegistry(mock(JdbcTemplate.class), factory);

        assertThatThrownBy(() -> registry.forEmbeddingModelId(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registry.forEmbeddingModelId("  ")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(factory);
    }

    @Test
    void forEmbeddingModelIdTrimsAndCachesPerEmbeddingModelId() {
        ProviderAwareEmbeddingModelFactory factory = mock(ProviderAwareEmbeddingModelFactory.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(factory.forModel(EMBEDDING_MODEL_ID)).thenReturn(embeddingModel);
        PgVectorStoreRegistry registry = new PgVectorStoreRegistry(mock(JdbcTemplate.class), factory);

        PgVectorStore first = registry.forEmbeddingModelId("  " + EMBEDDING_MODEL_ID + " ");
        PgVectorStore second = registry.forEmbeddingModelId(EMBEDDING_MODEL_ID);

        assertThat(first).isSameAs(second);
        verify(factory).forModel(EMBEDDING_MODEL_ID);
        verifyNoMoreInteractions(factory);
    }
}

package com.uniovi.rag.infrastructure.model;

import com.uniovi.rag.domain.AllowedModelType;
import com.uniovi.rag.infrastructure.persistence.AllowedModelRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.AllowedModelEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JpaModelCatalogAdapterTest {

    @Mock
    private AllowedModelRepository allowedModelRepository;

    @InjectMocks
    private JpaModelCatalogAdapter adapter;

    @Test
    void blockedLlmNamesInGovernance_filtersExplicitBlocksAndNonNullName() {
        AllowedModelEntity llmBlocked =
                AllowedModelEntity.newRow("blocked-chat", AllowedModelType.LLM, false, Instant.now());
        AllowedModelEntity llmAllowed =
                AllowedModelEntity.newRow("allowed-chat", AllowedModelType.LLM, true, Instant.now());
        AllowedModelEntity embedding =
                AllowedModelEntity.newRow("e1", AllowedModelType.EMBEDDING, false, Instant.now());
        AllowedModelEntity llmNullName = mock(AllowedModelEntity.class);
        when(llmNullName.getType()).thenReturn(AllowedModelType.LLM);
        when(llmNullName.isInAllowlist()).thenReturn(false);
        when(llmNullName.getName()).thenReturn(null);

        when(allowedModelRepository.findAll())
                .thenReturn(List.of(llmBlocked, llmAllowed, embedding, llmNullName));

        Set<String> names = adapter.blockedLlmNamesInGovernance();

        assertThat(names).containsExactly("blocked-chat");
    }

    @Test
    void blockedEmbeddingNamesInGovernance_filtersExplicitBlocks() {
        AllowedModelEntity embeddingBlocked =
                AllowedModelEntity.newRow("blocked-embed", AllowedModelType.EMBEDDING, false, Instant.now());
        AllowedModelEntity embeddingAllowed =
                AllowedModelEntity.newRow("allowed-embed", AllowedModelType.EMBEDDING, true, Instant.now());

        when(allowedModelRepository.findAll()).thenReturn(List.of(embeddingBlocked, embeddingAllowed));

        assertThat(adapter.blockedEmbeddingNamesInGovernance()).containsExactly("blocked-embed");
    }
}

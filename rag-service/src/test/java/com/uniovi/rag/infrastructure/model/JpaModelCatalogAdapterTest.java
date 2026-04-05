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
    void allowedLlmNamesInGovernance_filtersLlmAllowlistAndNonNullName() {
        AllowedModelEntity llmOk =
                AllowedModelEntity.newRow("m1", AllowedModelType.LLM, true, Instant.now());
        AllowedModelEntity embedding =
                AllowedModelEntity.newRow("e1", AllowedModelType.EMBEDDING, true, Instant.now());
        AllowedModelEntity llmNotListed =
                AllowedModelEntity.newRow("m2", AllowedModelType.LLM, false, Instant.now());
        AllowedModelEntity llmNullName = mock(AllowedModelEntity.class);
        when(llmNullName.getType()).thenReturn(AllowedModelType.LLM);
        when(llmNullName.isInAllowlist()).thenReturn(true);
        when(llmNullName.getName()).thenReturn(null);

        when(allowedModelRepository.findAll()).thenReturn(List.of(llmOk, embedding, llmNotListed, llmNullName));

        Set<String> names = adapter.allowedLlmNamesInGovernance();

        assertThat(names).containsExactly("m1");
    }
}

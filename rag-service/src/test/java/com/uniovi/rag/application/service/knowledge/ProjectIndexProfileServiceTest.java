package com.uniovi.rag.application.service.knowledge;

import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.application.service.llm.catalog.EmbeddingModelCatalogResolver;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.knowledge.ProjectIndexProfile;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import java.util.Map;
import com.uniovi.rag.infrastructure.llm.LlmProperties;
import com.uniovi.rag.infrastructure.persistence.ProjectIndexProfileRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectIndexProfileEntity;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.atLeastOnce;

@ExtendWith(MockitoExtension.class)
class ProjectIndexProfileServiceTest {

    @Mock
    private ProjectIndexProfileRepository repository;

    @Mock
    private LlmProperties llmProperties;

    @Mock
    private EmbeddingModelCatalogResolver embeddingModelCatalogResolver;

    @Mock
    private ResolvedLlmConfigResolver llmConfigResolver;

    private ProjectIndexProfileService sut;

    @BeforeEach
    void setUp() {
        when(llmProperties.effectiveDefaultEmbeddingModel()).thenReturn("deployment-default-embed");
        when(embeddingModelCatalogResolver.resolveForEffectiveProvider(any()))
                .thenAnswer(inv -> inv.getArgument(0));
        sut =
                new ProjectIndexProfileService(
                        repository,
                        llmProperties,
                        embeddingModelCatalogResolver,
                        llmConfigResolver,
                        400,
                        "CHUNK_LEVEL");
    }

    @Test
    void upsert_blankEmbedding_usesUserAssistantConfigurationDefault() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(llmConfigResolver.resolve(userId, null, null))
                .thenReturn(
                        ResolvedLlmConfig.uniform(
                                LlmProvider.OLLAMA_NATIVE,
                                "http://localhost:11434",
                                "chat-model",
                                "nomic-embed-text",
                                null,
                                null,
                                null,
                                null,
                                null,
                                Map.of()));
        when(repository.findById(projectId)).thenReturn(Optional.empty());
        when(repository.save(any(ProjectIndexProfileEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ProjectIndexProfile profile =
                sut.upsert(userId, projectId, MaterializationStrategy.CHUNK_LEVEL, false, null, null, 400, null);

        assertThat(profile.embeddingModelId()).isEqualTo("nomic-embed-text");
        ArgumentCaptor<ProjectIndexProfileEntity> captor = ArgumentCaptor.forClass(ProjectIndexProfileEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getEmbeddingModelId()).isEqualTo("nomic-embed-text");
        verify(llmConfigResolver).resolve(userId, null, null);
    }

    @Test
    void upsert_blankEmbedding_fallsBackToDeploymentDefaultWhenUserHasNoOverride() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(llmConfigResolver.resolve(userId, null, null))
                .thenReturn(
                        ResolvedLlmConfig.uniform(
                                LlmProvider.OLLAMA_NATIVE,
                                "http://localhost:11434",
                                "chat-model",
                                "",
                                null,
                                null,
                                null,
                                null,
                                null,
                                Map.of()));
        when(repository.findById(projectId)).thenReturn(Optional.empty());
        when(repository.save(any(ProjectIndexProfileEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ProjectIndexProfile profile =
                sut.upsert(userId, projectId, MaterializationStrategy.CHUNK_LEVEL, false, null, null, 400, null);

        assertThat(profile.embeddingModelId()).isEqualTo("deployment-default-embed");
        verify(embeddingModelCatalogResolver, atLeastOnce())
                .resolveForEffectiveProvider(eq("deployment-default-embed"));
    }

    @Test
    void upsert_explicitEmbedding_honorsRequestOverUserDefault() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(repository.findById(projectId)).thenReturn(Optional.empty());
        when(repository.save(any(ProjectIndexProfileEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ProjectIndexProfile profile =
                sut.upsert(
                        userId,
                        projectId,
                        MaterializationStrategy.HYBRID,
                        true,
                        null,
                        "bge-m3",
                        400,
                        null);

        assertThat(profile.embeddingModelId()).isEqualTo("bge-m3");
        verify(embeddingModelCatalogResolver, atLeastOnce()).resolveForEffectiveProvider(eq("bge-m3"));
    }
}

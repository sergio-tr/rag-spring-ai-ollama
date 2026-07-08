package com.uniovi.rag.application.service.knowledge;

import com.uniovi.rag.domain.ProjectDocumentStatus;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.knowledge.ProjectIndexProfile;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.interfaces.rest.dto.UpsertProjectIndexProfileRequest;
import com.uniovi.rag.application.service.project.ProjectAccessService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectIndexProfileApplicationServiceTest {

    @Mock
    private ProjectAccessService projectAccessService;

    @Mock
    private ProjectIndexProfileService projectIndexProfileService;

    @Mock
    private KnowledgeDocumentRepository knowledgeDocumentRepository;

    @InjectMocks
    private ProjectIndexProfileApplicationService sut;

    @Test
    void put_blocksWhenProjectHasReadyDocuments() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(projectAccessService.requireOwnedProject(userId, projectId)).thenReturn(mock(ProjectEntity.class));
        KnowledgeDocumentEntity ready = mock(KnowledgeDocumentEntity.class);
        when(ready.getStatus()).thenReturn(ProjectDocumentStatus.READY);
        when(knowledgeDocumentRepository.findByProject_IdAndCorpusScopeOrderByIdAsc(projectId, CorpusScope.PROJECT_SHARED))
                .thenReturn(List.of(ready));

        UpsertProjectIndexProfileRequest body =
                new UpsertProjectIndexProfileRequest("CHUNK_LEVEL", false, null, "nomic-embed-text", 400, null);

        assertThatThrownBy(() -> sut.put(userId, projectId, body))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex -> {
                            ResponseStatusException r = (ResponseStatusException) ex;
                            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                            assertThat(r.getReason()).contains("reindex");
                        });

        verify(projectIndexProfileService, never())
                .upsert(any(UUID.class), any(UUID.class), any(MaterializationStrategy.class), anyBoolean(), any(), any(), anyInt(), any());
    }

    @Test
    void put_rejectsStructuredSearchWithoutMetadata() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(projectAccessService.requireOwnedProject(userId, projectId)).thenReturn(mock(ProjectEntity.class));
        when(knowledgeDocumentRepository.findByProject_IdAndCorpusScopeOrderByIdAsc(projectId, CorpusScope.PROJECT_SHARED))
                .thenReturn(List.of());

        UpsertProjectIndexProfileRequest body =
                new UpsertProjectIndexProfileRequest("STRUCTURED_SEARCH", false, null, null, 400, null);

        assertThatThrownBy(() -> sut.put(userId, projectId, body))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex -> {
                            ResponseStatusException r = (ResponseStatusException) ex;
                            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                            assertThat(r.getReason()).contains("STRUCTURED_SEARCH requires metadata-aware indexing");
                        });

        verify(projectIndexProfileService, never())
                .upsert(any(UUID.class), any(UUID.class), any(MaterializationStrategy.class), anyBoolean(), any(), any(), anyInt(), any());
    }

    @Test
    void put_allowsStructuredSearchWithMetadata() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(projectAccessService.requireOwnedProject(userId, projectId)).thenReturn(mock(ProjectEntity.class));
        when(knowledgeDocumentRepository.findByProject_IdAndCorpusScopeOrderByIdAsc(projectId, CorpusScope.PROJECT_SHARED))
                .thenReturn(List.of());
        when(projectIndexProfileService.upsert(
                        userId,
                        projectId,
                        MaterializationStrategy.STRUCTURED_SEARCH,
                        true,
                        null,
                        null,
                        400,
                        null))
                .thenReturn(
                        new ProjectIndexProfile(
                                projectId,
                                MaterializationStrategy.STRUCTURED_SEARCH,
                                true,
                                null,
                                "embed",
                                400,
                                null,
                                "hash",
                                Instant.now(),
                                Instant.now()));

        UpsertProjectIndexProfileRequest body =
                new UpsertProjectIndexProfileRequest("STRUCTURED_SEARCH", true, null, null, 400, null);

        sut.put(userId, projectId, body);

        verify(projectIndexProfileService)
                .upsert(
                        userId,
                        projectId,
                        MaterializationStrategy.STRUCTURED_SEARCH,
                        true,
                        null,
                        null,
                        400,
                        null);
    }
}

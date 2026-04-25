package com.uniovi.rag.application.service.me;

import com.uniovi.rag.domain.ProjectDocumentStatus;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import com.uniovi.rag.interfaces.rest.dto.me.MeDocumentsPageResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeDocumentQueryServiceTest {

    @Mock
    private KnowledgeDocumentRepository knowledgeDocumentRepository;

    @InjectMocks
    private MeDocumentQueryService service;

    @Test
    void list_clampsSizeAndMapsRows() {
        UUID userId = UUID.randomUUID();
        KnowledgeDocumentEntity d = mock(KnowledgeDocumentEntity.class);
        UUID pid = UUID.randomUUID();
        com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity project =
                mock(com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity.class);
        when(project.getId()).thenReturn(pid);
        when(d.getId()).thenReturn(UUID.randomUUID());
        when(d.getProject()).thenReturn(project);
        when(d.getConversation()).thenReturn(null);
        when(d.getCorpusScope()).thenReturn(CorpusScope.PROJECT_SHARED);
        when(d.getFileName()).thenReturn("f");
        when(d.getStatus()).thenReturn(ProjectDocumentStatus.READY);
        when(d.getUploadedAt()).thenReturn(null);
        when(d.getReindexedAt()).thenReturn(null);
        when(d.getCurrentIndexSnapshot()).thenReturn(null);
        when(d.getChunkCount()).thenReturn(0);
        when(d.getStorageUri()).thenReturn(null);

        Page<KnowledgeDocumentEntity> page = new PageImpl<>(List.of(d));
        when(knowledgeDocumentRepository.searchForOwner(
                        eq(userId),
                        eq(null),
                        eq(CorpusScope.PROJECT_SHARED),
                        eq(null),
                        eq(null),
                        any(PageRequest.class)))
                .thenReturn(page);

        MeDocumentsPageResponse r = service.list(userId, 0, 500, CorpusScope.PROJECT_SHARED, null, null, null);
        assertEquals(1, r.items().size());
        assertEquals(1, r.total());
    }
}

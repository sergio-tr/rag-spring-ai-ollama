package com.uniovi.rag.application.service.me;

import com.uniovi.rag.infrastructure.persistence.ConversationRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.ProjectRepository;
import com.uniovi.rag.interfaces.rest.dto.me.MeSummaryResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeSummaryApplicationServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private KnowledgeDocumentRepository knowledgeDocumentRepository;

    @InjectMocks
    private MeSummaryApplicationService service;

    @Test
    void summarize_returnsCounts() {
        UUID userId = UUID.randomUUID();
        when(projectRepository.countByOwner_Id(userId)).thenReturn(2L);
        when(conversationRepository.countByUser_Id(userId)).thenReturn(3L);
        when(knowledgeDocumentRepository.countByProjectOwner_Id(userId)).thenReturn(5L);
        when(knowledgeDocumentRepository.sumByteSizeByProjectOwner_Id(userId)).thenReturn(99L);

        MeSummaryResponse r = service.summarize(userId);
        assertEquals(2, r.projectCount());
        assertEquals(3, r.conversationCount());
        assertEquals(5, r.documentCount());
        assertEquals(99, r.estimatedStorageBytes());
    }

    @Test
    void summarize_allZeros_whenRepositoriesReturnZero() {
        UUID userId = UUID.randomUUID();
        when(projectRepository.countByOwner_Id(userId)).thenReturn(0L);
        when(conversationRepository.countByUser_Id(userId)).thenReturn(0L);
        when(knowledgeDocumentRepository.countByProjectOwner_Id(userId)).thenReturn(0L);
        when(knowledgeDocumentRepository.sumByteSizeByProjectOwner_Id(userId)).thenReturn(0L);

        MeSummaryResponse r = service.summarize(userId);
        assertEquals(0, r.projectCount());
        assertEquals(0, r.conversationCount());
        assertEquals(0, r.documentCount());
        assertEquals(0, r.estimatedStorageBytes());
    }
}

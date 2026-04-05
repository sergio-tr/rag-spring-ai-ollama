package com.uniovi.rag.application.service;

import com.uniovi.rag.interfaces.rest.dto.MessageDto;
import com.uniovi.rag.infrastructure.persistence.ConversationRepository;
import com.uniovi.rag.infrastructure.persistence.MessageRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import com.uniovi.rag.service.preset.PresetService;
import com.uniovi.rag.service.project.ProjectAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationApplicationServiceTest {

    @Mock
    private ProjectAccessService projectAccessService;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private KnowledgeDocumentRepository knowledgeDocumentRepository;

    @Mock
    private PresetService presetService;

    @InjectMocks
    private ConversationApplicationService conversationApplicationService;

    @Test
    void listMessages_returnsEmptyWhenNoMessages() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        ConversationEntity conv = mock(ConversationEntity.class);
        when(projectAccessService.requireConversationForUser(userId, conversationId)).thenReturn(conv);
        when(messageRepository.findByConversation_IdAndDeletedAtIsNullOrderBySeqAsc(conversationId))
                .thenReturn(List.of());

        List<MessageDto> result = conversationApplicationService.listMessages(userId, conversationId);

        assertThat(result).isEmpty();
    }
}

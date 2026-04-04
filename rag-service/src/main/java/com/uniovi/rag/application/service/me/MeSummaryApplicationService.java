package com.uniovi.rag.application.service.me;

import com.uniovi.rag.infrastructure.persistence.ConversationRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.ProjectRepository;
import com.uniovi.rag.interfaces.rest.dto.me.MeSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class MeSummaryApplicationService {

    private final ProjectRepository projectRepository;
    private final ConversationRepository conversationRepository;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;

    public MeSummaryApplicationService(
            ProjectRepository projectRepository,
            ConversationRepository conversationRepository,
            KnowledgeDocumentRepository knowledgeDocumentRepository) {
        this.projectRepository = projectRepository;
        this.conversationRepository = conversationRepository;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
    }

    @Transactional(readOnly = true)
    public MeSummaryResponse summarize(UUID userId) {
        long projects = projectRepository.countByOwner_Id(userId);
        long conversations = conversationRepository.countByUser_Id(userId);
        long documents = knowledgeDocumentRepository.countByProjectOwner_Id(userId);
        long bytes = knowledgeDocumentRepository.sumByteSizeByProjectOwner_Id(userId);
        return new MeSummaryResponse(projects, conversations, documents, bytes);
    }
}

package com.uniovi.rag.application.service.me;

import com.uniovi.rag.application.port.BinaryStoragePort;
import com.uniovi.rag.domain.ProjectDocumentStatus;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import com.uniovi.rag.interfaces.rest.dto.me.MeDocumentsPageResponse;
import com.uniovi.rag.interfaces.rest.dto.me.UserDocumentRowDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class MeDocumentQueryService {

    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final BinaryStoragePort binaryStoragePort;

    public MeDocumentQueryService(
            KnowledgeDocumentRepository knowledgeDocumentRepository, BinaryStoragePort binaryStoragePort) {
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.binaryStoragePort = binaryStoragePort;
    }

    @Transactional(readOnly = true)
    public MeDocumentsPageResponse list(
            UUID userId,
            int page,
            int size,
            CorpusScope corpusScope,
            UUID projectId,
            UUID conversationId,
            ProjectDocumentStatus status) {
        int p = Math.max(0, page);
        int s = Math.min(Math.max(1, size), 200);
        PageRequest pr =
                PageRequest.of(p, s, Sort.by(Sort.Direction.DESC, "uploadedAt"));
        Page<KnowledgeDocumentEntity> slice =
                knowledgeDocumentRepository.searchForOwner(
                        userId, projectId, corpusScope, conversationId, status, pr);
        return new MeDocumentsPageResponse(
                slice.getContent().stream().map(this::toRow).toList(), slice.getTotalElements());
    }

    private UserDocumentRowDto toRow(KnowledgeDocumentEntity d) {
        String sig = null;
        if (d.getCurrentIndexSnapshot() != null) {
            sig = d.getCurrentIndexSnapshot().getSignatureHash();
        }
        boolean storagePresent =
                d.getStorageUri() != null
                        && !d.getStorageUri().isBlank()
                        && binaryStoragePort.isReadableNonEmpty(d.getStorageUri());
        return new UserDocumentRowDto(
                d.getId(),
                d.getProject().getId(),
                d.getConversation() != null ? d.getConversation().getId() : null,
                d.getCorpusScope(),
                d.getFileName(),
                d.getStatus(),
                d.getUploadedAt(),
                d.getReindexedAt(),
                sig,
                d.getChunkCount(),
                storagePresent);
    }
}

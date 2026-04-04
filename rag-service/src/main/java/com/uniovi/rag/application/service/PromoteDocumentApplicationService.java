package com.uniovi.rag.application.service;

import com.uniovi.rag.application.port.BinaryStoragePort;
import com.uniovi.rag.domain.ProjectDocumentStatus;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntityFactory;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.service.document.ProjectDocumentIngestionService;
import com.uniovi.rag.service.project.ProjectAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Promotes a CHAT_LOCAL document to PROJECT_SHARED (new row + re-ingest; overlay remains — DC-09).
 */
@Service
public class PromoteDocumentApplicationService {

    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final ProjectAccessService projectAccessService;
    private final ProjectDocumentIngestionService ingestionService;
    private final BinaryStoragePort binaryStoragePort;

    public PromoteDocumentApplicationService(
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            ProjectAccessService projectAccessService,
            ProjectDocumentIngestionService ingestionService,
            BinaryStoragePort binaryStoragePort) {
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.projectAccessService = projectAccessService;
        this.ingestionService = ingestionService;
        this.binaryStoragePort = binaryStoragePort;
    }

    @Transactional
    public void promote(UUID userId, UUID projectId, UUID sourceDocumentId) throws IOException {
        KnowledgeDocumentEntity src = projectAccessService.requireDocumentForUser(userId, sourceDocumentId);
        if (!projectId.equals(src.getProject().getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (src.getCorpusScope() != CorpusScope.CHAT_LOCAL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "document is not CHAT_LOCAL");
        }
        if (src.getStorageUri() == null || src.getStorageUri().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "no stored binary to promote");
        }
        ProjectEntity project = src.getProject();
        KnowledgeDocumentEntity promoted = KnowledgeDocumentEntityFactory.newIngesting(project, src.getFileName());
        promoted = knowledgeDocumentRepository.save(promoted);

        BinaryStoragePort.StoredObject copied = binaryStoragePort.linkOrCopy(
                src.getStorageUri(),
                project.getId() + "/" + promoted.getId() + "/promoted.bin");
        promoted.setStorageUri(copied.relativeUri());
        promoted.setContentChecksum(copied.sha256Hex());
        promoted.setMimeType(src.getMimeType());
        promoted.setByteSize(src.getByteSize());
        promoted.setStatus(ProjectDocumentStatus.INGESTING);
        knowledgeDocumentRepository.save(promoted);

        Path temp = Files.createTempFile("rag-promote-", ".bin");
        try (InputStream in = binaryStoragePort.openStream(copied.relativeUri())) {
            Files.copy(in, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        ingestionService.ingestFromTempFile(
                project.getId(), promoted.getId(), temp, src.getFileName(), src.getMimeType() != null ? src.getMimeType() : "application/octet-stream");
    }
}

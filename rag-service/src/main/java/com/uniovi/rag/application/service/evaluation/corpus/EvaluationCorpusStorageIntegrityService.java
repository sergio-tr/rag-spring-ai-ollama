package com.uniovi.rag.application.service.evaluation.corpus;

import com.uniovi.rag.application.port.BinaryStoragePort;
import com.uniovi.rag.domain.ProjectDocumentStatus;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Verifies evaluation-corpus document binaries exist before readiness and index preparation. */
@Service
public class EvaluationCorpusStorageIntegrityService {

    private final BinaryStoragePort binaryStoragePort;

    public EvaluationCorpusStorageIntegrityService(BinaryStoragePort binaryStoragePort) {
        this.binaryStoragePort = binaryStoragePort;
    }

    public boolean isBinaryPresent(KnowledgeDocumentEntity doc) {
        if (doc == null) {
            return false;
        }
        String uri = doc.getStorageUri();
        if (uri == null || uri.isBlank()) {
            return false;
        }
        return binaryStoragePort.isReadableNonEmpty(uri);
    }

    public boolean isStorageReady(KnowledgeDocumentEntity doc) {
        return doc != null && doc.getStatus() == ProjectDocumentStatus.READY && isBinaryPresent(doc);
    }

    public boolean hasReadyDocumentWithMissingBinary(List<KnowledgeDocumentEntity> docs) {
        if (docs == null || docs.isEmpty()) {
            return false;
        }
        for (KnowledgeDocumentEntity doc : docs) {
            if (doc != null && doc.getStatus() == ProjectDocumentStatus.READY && !isBinaryPresent(doc)) {
                return true;
            }
        }
        return false;
    }

    public List<UUID> storageReadyDocumentIds(List<KnowledgeDocumentEntity> docs) {
        List<UUID> ids = new ArrayList<>();
        if (docs == null) {
            return List.of();
        }
        for (KnowledgeDocumentEntity doc : docs) {
            if (isStorageReady(doc)) {
                ids.add(doc.getId());
            }
        }
        return List.copyOf(new LinkedHashSet<>(ids));
    }
}

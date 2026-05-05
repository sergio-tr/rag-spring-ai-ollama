package com.uniovi.rag.application.service.knowledge;

import com.uniovi.rag.domain.ProjectDocumentStatus;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ensures documents never remain stuck in INGESTING forever.
 * <p>
 * This is a safety net for unexpected process crashes, executor starvation, or unhandled async errors.
 */
@Service
public class DocumentIngestionWatchdog {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionWatchdog.class);

    private static final Duration STALL_THRESHOLD = Duration.ofMinutes(30);

    private final KnowledgeDocumentRepository knowledgeDocumentRepository;

    public DocumentIngestionWatchdog(KnowledgeDocumentRepository knowledgeDocumentRepository) {
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
    }

    @Scheduled(fixedDelayString = "${rag.documents.watchdog.fixedDelayMs:300000}")
    @Transactional
    public void markStalledIngestsAsError() {
        Instant cutoff = Instant.now().minus(STALL_THRESHOLD);
        // We do not have an explicit "ingest started at" column; use reindexedAt when present, otherwise uploadedAt.
        List<KnowledgeDocumentEntity> ingesting = knowledgeDocumentRepository.findByStatus(ProjectDocumentStatus.INGESTING);
        for (KnowledgeDocumentEntity d : ingesting) {
            Instant since = d.getReindexedAt() != null ? d.getReindexedAt() : d.getUploadedAt();
            if (since != null && since.isBefore(cutoff)) {
                d.setStatus(ProjectDocumentStatus.ERROR);
                d.setErrorMessage("Ingestion timed out (watchdog). Please retry.");
                knowledgeDocumentRepository.save(d);
                log.warn("document_ingest_watchdog_marked_error documentId={} fileName={}", d.getId(), d.getFileName());
            }
        }
    }
}


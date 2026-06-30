package com.uniovi.rag.application.service.evaluation.corpus;

import com.uniovi.rag.application.evaluation.workbook.EvaluationReferenceBundleLoader;
import com.uniovi.rag.application.evaluation.workbook.ReferenceBundleSnapshot;
import com.uniovi.rag.domain.ProjectDocumentStatus;
import com.uniovi.rag.domain.evaluation.workbook.ChunkRegistryEntry;
import com.uniovi.rag.domain.evaluation.workbook.EvaluationWorkbook;
import com.uniovi.rag.infrastructure.persistence.EvaluationCorpusDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationCorpusRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationCorpusEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import com.uniovi.rag.application.service.knowledge.KnowledgeIngestionService;
import com.uniovi.rag.interfaces.rest.dto.ProjectDocumentDto;
import com.uniovi.rag.interfaces.rest.dto.evaluation.EvaluationCorpusGoldAlignmentDto;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Builds a Lab evaluation corpus from the packaged reference workbook {@code chunk_registry} rows,
 * preserving ACTA_* document and chunk ids for embedding retrieval scoring.
 */
@Service
public class EvaluationCorpusGoldAlignmentService {

    public static final String GOLD_ALIGNED_CORPUS_NAME = "Gold-aligned reference bundle (Gate 2)";

    private final EvaluationReferenceBundleLoader referenceBundleLoader;
    private final EvaluationCorpusApplicationService evaluationCorpusApplicationService;
    private final EvaluationCorpusRepository evaluationCorpusRepository;
    private final EvaluationCorpusDocumentRepository evaluationCorpusDocumentRepository;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final KnowledgeIngestionService knowledgeIngestionService;

    public EvaluationCorpusGoldAlignmentService(
            EvaluationReferenceBundleLoader referenceBundleLoader,
            EvaluationCorpusApplicationService evaluationCorpusApplicationService,
            EvaluationCorpusRepository evaluationCorpusRepository,
            EvaluationCorpusDocumentRepository evaluationCorpusDocumentRepository,
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            KnowledgeIngestionService knowledgeIngestionService) {
        this.referenceBundleLoader = referenceBundleLoader;
        this.evaluationCorpusApplicationService = evaluationCorpusApplicationService;
        this.evaluationCorpusRepository = evaluationCorpusRepository;
        this.evaluationCorpusDocumentRepository = evaluationCorpusDocumentRepository;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.knowledgeIngestionService = knowledgeIngestionService;
    }

    @Transactional(readOnly = true)
    public EvaluationCorpusGoldAlignmentDto previewAlignment(UUID userId) {
        ReferenceBundleSnapshot snap = requireValidBundle();
        EvaluationGoldCorpusAlignmentVerifier.AlignmentReport report =
                EvaluationGoldCorpusAlignmentVerifier.verifyWorkbook(snap.workbook());
        return new EvaluationCorpusGoldAlignmentDto(
                null,
                snap.workbook().chunkRegistry().size(),
                snap.workbook().corpusDocuments().size(),
                snap.workbook().embeddingRetrievalQueries().size(),
                report.aligned(),
                report.violations(),
                List.of(),
                snap.sha256Hex().orElse(null));
    }

    public EvaluationCorpusGoldAlignmentDto alignFromReferenceBundle(UUID userId, boolean replaceExisting) {
        ReferenceBundleSnapshot snap = requireValidBundle();
        EvaluationWorkbook workbook = snap.workbook();
        EvaluationGoldCorpusAlignmentVerifier.AlignmentReport report =
                EvaluationGoldCorpusAlignmentVerifier.verifyWorkbook(workbook);
        if (!report.aligned()) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Workbook gold alignment verification failed: " + String.join("; ", report.violations()));
        }

        EvaluationCorpusEntity corpus = resolveCorpus(userId, replaceExisting);
        UUID corpusId = corpus.getId();
        UUID indexProjectId = evaluationCorpusApplicationService.resolveIndexProjectId(corpus);
        if (indexProjectId == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Evaluation corpus has no index scope.");
        }

        if (replaceExisting) {
            evaluationCorpusApplicationService.removeAllDocuments(userId, corpusId);
        }

        List<Map<String, Object>> indexedRows = new ArrayList<>();
        int ingested = 0;
        int skipped = 0;
        for (ChunkRegistryEntry entry : workbook.chunkRegistry()) {
            if (entry == null
                    || entry.documentId() == null
                    || entry.documentId().isBlank()
                    || entry.chunkId() == null
                    || entry.chunkId().isBlank()) {
                skipped++;
                continue;
            }
            String text = entry.goldEvidenceText() != null ? entry.goldEvidenceText().trim() : "";
            if (text.isEmpty()) {
                skipped++;
                continue;
            }
            String filename =
                    EvaluationGoldCorpusFilenameSupport.buildFilename(entry.documentId(), entry.chunkId());
            if (documentAlreadyPresent(corpusId, indexProjectId, filename)) {
                skipped++;
                indexedRows.add(rowSummary(entry, filename, "REUSED"));
                continue;
            }
            try {
                ProjectDocumentDto dto =
                        knowledgeIngestionService.ingestProjectSharedDocumentSynchronouslyFromBytes(
                                userId,
                                indexProjectId,
                                text.getBytes(StandardCharsets.UTF_8),
                                filename,
                                "text/plain");
                evaluationCorpusDocumentRepository.save(
                        com.uniovi.rag.infrastructure.persistence.jpa.EvaluationCorpusDocumentEntity.link(
                                corpusId, dto.id(), Instant.now()));
                ingested++;
                indexedRows.add(rowSummary(entry, filename, dto.status() != null ? dto.status().name() : "READY"));
            } catch (IOException ex) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Failed to ingest gold chunk " + entry.chunkId() + ": " + ex.getMessage());
            }
        }

        corpus.setUpdatedAt(Instant.now());
        evaluationCorpusRepository.save(corpus);

        return new EvaluationCorpusGoldAlignmentDto(
                corpusId,
                workbook.chunkRegistry().size(),
                workbook.corpusDocuments().size(),
                workbook.embeddingRetrievalQueries().size(),
                true,
                List.of(),
                indexedRows,
                snap.sha256Hex().orElse(null));
    }

    private ReferenceBundleSnapshot requireValidBundle() {
        ReferenceBundleSnapshot snap = referenceBundleLoader.getSnapshot();
        if (!snap.classpathResourcePresent() || snap.workbook() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Reference bundle is not available.");
        }
        if (snap.validationReport() != null && snap.validationReport().hasErrors()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Reference bundle validation failed.");
        }
        return snap;
    }

    private EvaluationCorpusEntity resolveCorpus(UUID userId, boolean replaceExisting) {
        List<EvaluationCorpusEntity> existing =
                evaluationCorpusRepository.findByOwner_IdAndName(userId, GOLD_ALIGNED_CORPUS_NAME);
        if (!existing.isEmpty()) {
            return existing.getFirst();
        }
        UUID corpusId =
                evaluationCorpusApplicationService
                        .create(
                                userId,
                                new com.uniovi.rag.interfaces.rest.dto.evaluation.EvaluationCorpusCreateRequest(
                                        GOLD_ALIGNED_CORPUS_NAME))
                        .id();
        return evaluationCorpusRepository
                .findByIdAndOwner_Id(corpusId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Corpus create failed"));
    }

    private boolean documentAlreadyPresent(UUID corpusId, UUID indexProjectId, String filename) {
        for (KnowledgeDocumentEntity doc : evaluationCorpusDocumentRepository.findDocumentsByCorpusId(corpusId)) {
            if (doc == null || doc.getFileName() == null) {
                continue;
            }
            if (!doc.getFileName().equalsIgnoreCase(filename)) {
                continue;
            }
            if (doc.getStatus() == ProjectDocumentStatus.READY) {
                return true;
            }
        }
        Optional<KnowledgeDocumentEntity> byName =
                knowledgeDocumentRepository.findFirstByProject_IdAndFileNameAndCorpusScopeAndConversationIsNull(
                        indexProjectId, filename, com.uniovi.rag.domain.knowledge.CorpusScope.PROJECT_SHARED);
        return byName.filter(d -> d.getStatus() == ProjectDocumentStatus.READY).isPresent();
    }

    private static Map<String, Object> rowSummary(ChunkRegistryEntry entry, String filename, String status) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("workbookDocumentId", entry.documentId());
        row.put("workbookChunkId", entry.chunkId());
        row.put("filename", filename);
        row.put("status", status);
        row.put("goldIdsPreserved", true);
        return row;
    }
}

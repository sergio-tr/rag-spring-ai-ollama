package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.application.evaluation.workbook.EvaluationReferenceBundleLoader;
import com.uniovi.rag.application.evaluation.workbook.EvaluationWorkbookParser;
import com.uniovi.rag.application.evaluation.workbook.ExperimentalDatasetKindMapping;
import com.uniovi.rag.application.evaluation.workbook.ExperimentalDatasetMetrics;
import com.uniovi.rag.application.evaluation.workbook.ExperimentalDatasetTemplateFactory;
import com.uniovi.rag.application.evaluation.workbook.ReferenceBundleSnapshot;
import com.uniovi.rag.application.port.EvaluationDatasetStorePort;
import com.uniovi.rag.domain.evaluation.workbook.ExperimentalDatasetType;
import com.uniovi.rag.domain.evaluation.workbook.ValidationIssuePayload;
import com.uniovi.rag.domain.evaluation.workbook.ValidationReport;
import com.uniovi.rag.domain.evaluation.workbook.WorkbookParseResult;
import com.uniovi.rag.domain.evaluation.EvaluationDatasetScope;
import com.uniovi.rag.domain.EvaluationDatasetType;
import com.uniovi.rag.infrastructure.persistence.EvaluationDatasetRepository;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationDatasetEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.interfaces.rest.dto.experimental.ExperimentalDatasetListItemDto;
import com.uniovi.rag.interfaces.rest.dto.experimental.ExperimentalDatasetUploadResponseDto;
import com.uniovi.rag.interfaces.rest.dto.experimental.ExperimentalDatasetValidationReportDto;
import com.uniovi.rag.interfaces.rest.dto.experimental.ValidationIssueDto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Lab experimental datasets: templates, upload + validation, listing (including read-only reference entry).
 */
@Service
public class ExperimentalDatasetLabService {

    /** Stable synthetic id for the packaged reference workbook row in {@code GET …/experimental-datasets}. */
    public static final UUID REFERENCE_DATASET_LIST_ENTRY_ID =
            UUID.fromString("00000000-0000-7000-8000-000000000001");

    private final EvaluationDatasetRepository evaluationDatasetRepository;
    private final UserRepository userRepository;
    private final EvaluationDatasetStorePort evaluationDatasetStorePort;
    private final EvaluationWorkbookParser evaluationWorkbookParser;
    private final EvaluationReferenceBundleLoader referenceBundleLoader;

    public ExperimentalDatasetLabService(
            EvaluationDatasetRepository evaluationDatasetRepository,
            UserRepository userRepository,
            EvaluationDatasetStorePort evaluationDatasetStorePort,
            EvaluationWorkbookParser evaluationWorkbookParser,
            EvaluationReferenceBundleLoader referenceBundleLoader) {
        this.evaluationDatasetRepository = evaluationDatasetRepository;
        this.userRepository = userRepository;
        this.evaluationDatasetStorePort = evaluationDatasetStorePort;
        this.evaluationWorkbookParser = evaluationWorkbookParser;
        this.referenceBundleLoader = referenceBundleLoader;
    }

    public byte[] templateBytes(ExperimentalDatasetType kind) throws IOException {
        return ExperimentalDatasetTemplateFactory.buildTemplate(kind);
    }

    @Transactional
    public ExperimentalDatasetUploadResponseDto upload(
            UUID userId, MultipartFile file, String datasetTypeRaw, String name, String description)
            throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file is required");
        }
        ExperimentalDatasetType experimentalType =
                ExperimentalDatasetKindMapping.parseUploadDatasetType(datasetTypeRaw)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST, "Unknown or missing datasetType"));
        if (experimentalType == ExperimentalDatasetType.REFERENCE_BUNDLE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "REFERENCE_BUNDLE cannot be uploaded via this endpoint");
        }
        byte[] bytes = file.getBytes();
        WorkbookParseResult parsed =
                evaluationWorkbookParser.parse(new ByteArrayInputStream(bytes), experimentalType);
        if (parsed.validationReport().hasErrors()) {
            throw new ExperimentalDatasetValidationException(parsed.validationReport());
        }

        UserEntity owner =
                userRepository.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        String original = file.getOriginalFilename();
        String safeName = original != null && !original.isBlank() ? original : "dataset.xlsx";
        String pending = UUID.randomUUID().toString();
        String relativeKey = "users/" + userId + "/" + pending + "-" + sanitizeFilename(safeName);

        EvaluationDatasetStorePort.StoredDataset stored =
                evaluationDatasetStorePort.store(new ByteArrayInputStream(bytes), bytes.length, relativeKey);

        int qc = ExperimentalDatasetMetrics.primaryRowCount(parsed.workbook(), experimentalType);
        int rows = ExperimentalDatasetMetrics.totalRowCount(parsed.workbook(), experimentalType);

        ExperimentalDatasetValidationReportDto reportDto =
                ExperimentalDatasetValidationReportDto.from(parsed.validationReport());
        List<ValidationIssuePayload> payloads = reportDto.toPayloads();

        EvaluationDatasetEntity entity = EvaluationDatasetEntity.newLabUploadPlaceholder();
        entity.setOwner(owner);
        entity.setName(name != null && !name.isBlank() ? name.trim() : stripExtension(safeName));
        entity.setDescription(description != null ? description.trim() : null);
        entity.setFileName(safeName);
        entity.setQuestionCount(qc);
        entity.setSha256(stored.sha256Hex());
        entity.setType(ExperimentalDatasetKindMapping.toPersistedCoarseType(experimentalType));
        entity.setExperimentalKind(experimentalType.name());
        entity.setUploadedAt(Instant.now());
        entity.setValidatedAt(Instant.now());
        entity.setDatasetScope(EvaluationDatasetScope.USER_DATASET.name());
        entity.setStorageUri(stored.storageUri());
        entity.setByteSize(stored.byteSize());
        entity.setMimeType(
                file.getContentType() != null && !file.getContentType().isBlank()
                        ? file.getContentType()
                        : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        entity.setSchemaVersion("experimental-workbook-v1");
        entity.setValidationStatus("VALID");
        entity.setValidationIssues(payloads);

        evaluationDatasetRepository.save(entity);

        return new ExperimentalDatasetUploadResponseDto(
                entity.getId(),
                experimentalType.name(),
                entity.getType().name(),
                "VALID",
                qc,
                rows,
                reportDto);
    }

    @Transactional(readOnly = true)
    public List<ExperimentalDatasetListItemDto> listForUser(UUID userId) {
        List<ExperimentalDatasetListItemDto> out = new ArrayList<>();
        ReferenceBundleSnapshot snap = referenceBundleLoader.getSnapshot();
        if (snap.classpathResourcePresent()) {
            int qc = snap.workbook().llmReaderQuestions().size();
            int total =
                    snap.counts().llmReaderQuestions()
                            + snap.counts().embeddingRetrievalQueries()
                            + snap.counts().ragPresetQuestions()
                            + snap.counts().corpusDocuments()
                            + snap.counts().chunkRegistryEntries();
            out.add(
                    new ExperimentalDatasetListItemDto(
                            REFERENCE_DATASET_LIST_ENTRY_ID,
                            "Packaged reference workbook",
                            ExperimentalDatasetType.REFERENCE_BUNDLE.name(),
                            EvaluationDatasetType.RAG.name(),
                            true,
                            qc,
                            total,
                            snap.validForReferenceUse() ? "VALID" : "INVALID",
                            null,
                            "Internal classpath bundle (read-only)."));
        }
        List<EvaluationDatasetEntity> owned =
                evaluationDatasetRepository.findByOwner_IdOrderByUploadedAtDesc(userId);
        for (EvaluationDatasetEntity e : owned) {
            out.add(toListItem(e));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public ExperimentalDatasetValidationReportDto validationReport(UUID userId, UUID datasetId) {
        if (REFERENCE_DATASET_LIST_ENTRY_ID.equals(datasetId)) {
            ReferenceBundleSnapshot snap = referenceBundleLoader.getSnapshot();
            return ExperimentalDatasetValidationReportDto.from(snap.validationReport());
        }
        EvaluationDatasetEntity e =
                evaluationDatasetRepository
                        .findById(datasetId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dataset not found"));
        authorizeOwner(userId, e);
        if (e.getExperimentalKind() == null || e.getExperimentalKind().isBlank()) {
            return snapshotFromEntity(e);
        }
        ExperimentalDatasetType experimentalType;
        try {
            experimentalType = ExperimentalDatasetType.valueOf(e.getExperimentalKind());
        } catch (IllegalArgumentException ex) {
            return snapshotFromEntity(e);
        }
        try (InputStream in = evaluationDatasetStorePort.openStream(e.getStorageUri())) {
            WorkbookParseResult parsed = evaluationWorkbookParser.parse(in, experimentalType);
            return ExperimentalDatasetValidationReportDto.from(parsed.validationReport());
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to read dataset binary");
        }
    }

    private static void authorizeOwner(UUID userId, EvaluationDatasetEntity e) {
        if (e.getOwner() == null || !userId.equals(e.getOwner().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Dataset not owned by user");
        }
    }

    private static ExperimentalDatasetValidationReportDto snapshotFromEntity(EvaluationDatasetEntity e) {
        List<ValidationIssuePayload> payloads = e.getValidationIssues();
        if (payloads == null || payloads.isEmpty()) {
            return new ExperimentalDatasetValidationReportDto(List.of(), false, false);
        }
        var issues =
                payloads.stream()
                        .map(
                                p ->
                                        new ValidationIssueDto(
                                                p.severity(),
                                                p.code(),
                                                p.sheet(),
                                                p.rowNumber(),
                                                p.column(),
                                                p.message()))
                        .toList();
        boolean hasErrors = issues.stream().anyMatch(i -> "ERROR".equalsIgnoreCase(i.severity()));
        boolean hasWarnings = issues.stream().anyMatch(i -> "WARNING".equalsIgnoreCase(i.severity()));
        return new ExperimentalDatasetValidationReportDto(issues, hasErrors, hasWarnings);
    }

    private static ExperimentalDatasetListItemDto toListItem(EvaluationDatasetEntity e) {
        Integer qc = e.getQuestionCount();
        int q = qc != null ? qc : 0;
        String expKind =
                e.getExperimentalKind() != null && !e.getExperimentalKind().isBlank()
                        ? e.getExperimentalKind()
                        : "UNKNOWN_LEGACY";
        return new ExperimentalDatasetListItemDto(
                e.getId(),
                e.getName(),
                expKind,
                e.getType().name(),
                false,
                qc,
                q,
                e.getValidationStatus(),
                e.getUploadedAt(),
                e.getDescription());
    }

    private static String sanitizeFilename(String name) {
        return name.replace("..", "_").replace("/", "_").replace("\\", "_");
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}

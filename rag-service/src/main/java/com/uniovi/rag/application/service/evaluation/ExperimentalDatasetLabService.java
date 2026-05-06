package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.application.evaluation.workbook.EvaluationReferenceBundleLoader;
import com.uniovi.rag.application.evaluation.workbook.EvaluationWorkbookParser;
import com.uniovi.rag.application.evaluation.workbook.ExperimentalDatasetKindMapping;
import com.uniovi.rag.application.evaluation.workbook.ExperimentalDatasetMetrics;
import com.uniovi.rag.application.evaluation.workbook.ExperimentalDatasetTemplateFactory;
import com.uniovi.rag.application.evaluation.workbook.ReferenceBundleSnapshot;
import com.uniovi.rag.application.evaluation.workbook.LabDatasetGateValidator;
import com.uniovi.rag.application.port.EvaluationDatasetStorePort;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.domain.evaluation.workbook.EvaluationWorkbook;
import com.uniovi.rag.domain.evaluation.workbook.ExperimentalDatasetType;
import com.uniovi.rag.domain.evaluation.workbook.ValidationIssue;
import com.uniovi.rag.domain.evaluation.workbook.ValidationIssuePayload;
import com.uniovi.rag.domain.evaluation.workbook.ValidationReport;
import com.uniovi.rag.domain.evaluation.workbook.ValidationIssueCode;
import com.uniovi.rag.domain.evaluation.workbook.ValidationSeverity;
import com.uniovi.rag.domain.evaluation.workbook.WorkbookParseResult;
import com.uniovi.rag.domain.evaluation.EvaluationDatasetScope;
import com.uniovi.rag.domain.EvaluationDatasetType;
import com.uniovi.rag.infrastructure.persistence.EvaluationDatasetRepository;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationDatasetEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.interfaces.rest.dto.experimental.ExperimentalDatasetListItemDto;
import com.uniovi.rag.interfaces.rest.dto.experimental.ExperimentalDatasetQuestionCountsDto;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
            ExperimentalDatasetQuestionCountsDto counts =
                    new ExperimentalDatasetQuestionCountsDto(
                            snap.counts().llmReaderQuestions(),
                            snap.counts().embeddingRetrievalQueries(),
                            snap.counts().ragPresetQuestions(),
                            snap.counts().presets(),
                            snap.counts().chunkRegistryEntries());

            boolean isDemo = isDemoWorkbook(snap.workbook(), snap.validationReport());
            boolean canRunLlm = canRunKind(BenchmarkKind.LLM_JUDGE_QA, ExperimentalDatasetType.REFERENCE_BUNDLE, snap.workbook());
            boolean canRunEmb = canRunKind(BenchmarkKind.EMBEDDING_RETRIEVAL, ExperimentalDatasetType.REFERENCE_BUNDLE, snap.workbook());
            boolean canRunRag = canRunKind(BenchmarkKind.RAG_PRESET_END_TO_END, ExperimentalDatasetType.REFERENCE_BUNDLE, snap.workbook());

            List<ValidationIssueDto> mergedIssues = mergeIssues(snap.validationReport(), ExperimentalDatasetType.REFERENCE_BUNDLE, snap.workbook());
            String status = snap.validForReferenceUse() && !isDemo ? "VALID" : "INVALID";
            out.add(
                    new ExperimentalDatasetListItemDto(
                            REFERENCE_DATASET_LIST_ENTRY_ID,
                            "Packaged reference workbook",
                            ExperimentalDatasetType.REFERENCE_BUNDLE.name(),
                            EvaluationDatasetType.RAG.name(),
                            true,
                            status,
                            counts,
                            true,
                            isDemo,
                            canRunLlm,
                            canRunEmb,
                            canRunRag,
                            mergedIssues,
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

    private ExperimentalDatasetListItemDto toListItem(EvaluationDatasetEntity e) {
        String expKind =
                e.getExperimentalKind() != null && !e.getExperimentalKind().isBlank()
                        ? e.getExperimentalKind()
                        : "UNKNOWN_LEGACY";

        ExperimentalDatasetType experimentalType = parseExperimentalType(expKind);
        if (experimentalType == null) {
            return new ExperimentalDatasetListItemDto(
                    e.getId(),
                    e.getName(),
                    expKind,
                    e.getType().name(),
                    false,
                    "INVALID",
                    new ExperimentalDatasetQuestionCountsDto(0, 0, 0, 0, 0),
                    false,
                    false,
                    false,
                    false,
                    false,
                    issuesFromEntity(e),
                    e.getUploadedAt(),
                    e.getDescription());
        }

        WorkbookParseResult parsed = null;
        try (InputStream in = evaluationDatasetStorePort.openStream(e.getStorageUri())) {
            parsed = evaluationWorkbookParser.parse(in, experimentalType);
        } catch (IOException ex) {
            return new ExperimentalDatasetListItemDto(
                    e.getId(),
                    e.getName(),
                    experimentalType.name(),
                    e.getType().name(),
                    false,
                    "INVALID",
                    new ExperimentalDatasetQuestionCountsDto(0, 0, 0, 0, 0),
                    false,
                    false,
                    false,
                    false,
                    false,
                    List.of(new ValidationIssueDto(
                            ValidationSeverity.ERROR.name(),
                            ValidationIssueCode.WORKBOOK_IO_ERROR.name(),
                            "",
                            0,
                            "",
                            "Failed to read dataset binary")),
                    e.getUploadedAt(),
                    e.getDescription());
        }

        EvaluationWorkbook wb = parsed.workbook();
        ExperimentalDatasetQuestionCountsDto counts = countsForWorkbook(wb);

        boolean isDemo = isDemoWorkbook(wb, parsed.validationReport());
        boolean isTemplateOnly = counts.llmReaderQuestions() == 0
                && counts.embeddingQueries() == 0
                && counts.ragPresetQuestions() == 0;

        boolean canRunLlm = canRunKind(BenchmarkKind.LLM_JUDGE_QA, experimentalType, wb);
        boolean canRunEmb = canRunKind(BenchmarkKind.EMBEDDING_RETRIEVAL, experimentalType, wb);
        boolean canRunRag = canRunKind(BenchmarkKind.RAG_PRESET_END_TO_END, experimentalType, wb);

        List<ValidationIssueDto> mergedIssues = mergeIssues(parsed.validationReport(), experimentalType, wb);

        String status;
        if (isDemo) {
            status = "INVALID";
        } else if (isTemplateOnly) {
            status = "TEMPLATE_ONLY";
        } else if (parsed.validationReport().hasErrors()) {
            status = "INVALID";
        } else {
            status = "VALID";
        }

        return new ExperimentalDatasetListItemDto(
                e.getId(),
                e.getName(),
                experimentalType.name(),
                e.getType().name(),
                false,
                status,
                counts,
                false,
                isDemo,
                canRunLlm,
                canRunEmb,
                canRunRag,
                mergedIssues,
                e.getUploadedAt(),
                e.getDescription());
    }

    private static ExperimentalDatasetType parseExperimentalType(String expKind) {
        if (expKind == null || expKind.isBlank()) {
            return null;
        }
        try {
            return ExperimentalDatasetType.valueOf(expKind);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static ExperimentalDatasetQuestionCountsDto countsForWorkbook(EvaluationWorkbook wb) {
        if (wb == null) {
            return new ExperimentalDatasetQuestionCountsDto(0, 0, 0, 0, 0);
        }
        return new ExperimentalDatasetQuestionCountsDto(
                wb.llmReaderQuestions().size(),
                wb.embeddingRetrievalQueries().size(),
                wb.ragPresetQuestionsEnriched().size(),
                wb.ragPresetCatalog().size(),
                wb.chunkRegistry().size());
    }

    private static boolean isDemoWorkbook(EvaluationWorkbook wb, ValidationReport structural) {
        if (structural != null
                && structural.issues().stream()
                        .anyMatch(i -> ValidationIssueCode.DATASET_DEMO_CONTENT_DETECTED == i.code())) {
            return true;
        }
        if (wb == null) {
            return false;
        }
        return wb.ragPresetQuestionsEnriched().stream().anyMatch(q -> "RAG_Q1".equalsIgnoreCase(q.id()))
                || wb.llmReaderQuestions().stream().anyMatch(q -> "RAG_Q1".equalsIgnoreCase(q.id()));
    }

    private boolean canRunKind(BenchmarkKind kind, ExperimentalDatasetType type, EvaluationWorkbook wb) {
        if (kind == null || type == null) {
            return false;
        }
        if (kind == BenchmarkKind.LLM_JUDGE_QA
                && !(type == ExperimentalDatasetType.LLM_MODEL_BASELINE || type == ExperimentalDatasetType.REFERENCE_BUNDLE)) {
            return false;
        }
        if (kind == BenchmarkKind.EMBEDDING_RETRIEVAL
                && !(type == ExperimentalDatasetType.EMBEDDING_MODEL_BASELINE || type == ExperimentalDatasetType.REFERENCE_BUNDLE)) {
            return false;
        }
        if (kind == BenchmarkKind.RAG_PRESET_END_TO_END
                && !(type == ExperimentalDatasetType.RAG_PRESET_BENCHMARK || type == ExperimentalDatasetType.REFERENCE_BUNDLE)) {
            return false;
        }
        ValidationReport gate = new ValidationReport();
        LabDatasetGateValidator.validatePreRun(kind, type, wb, gate);
        return !gate.hasErrors();
    }

    private static List<ValidationIssueDto> mergeIssues(
            ValidationReport structural, ExperimentalDatasetType type, EvaluationWorkbook wb) {
        Map<String, ValidationIssueDto> unique = new LinkedHashMap<>();
        if (structural != null) {
            for (ValidationIssue i : structural.issues()) {
                ValidationIssueDto dto = ValidationIssueDto.from(i);
                unique.put(key(dto), dto);
            }
        }
        for (BenchmarkKind k :
                List.of(BenchmarkKind.LLM_JUDGE_QA, BenchmarkKind.EMBEDDING_RETRIEVAL, BenchmarkKind.RAG_PRESET_END_TO_END)) {
            ValidationReport gate = new ValidationReport();
            LabDatasetGateValidator.validatePreRun(k, type, wb, gate);
            for (ValidationIssue i : gate.issues()) {
                ValidationIssueDto dto = ValidationIssueDto.from(i);
                unique.putIfAbsent(key(dto), dto);
            }
        }
        return unique.values().stream().toList();
    }

    private static String key(ValidationIssueDto dto) {
        if (dto == null) {
            return "";
        }
        return String.valueOf(dto.code())
                + "|" + String.valueOf(dto.sheet())
                + "|" + dto.rowNumber()
                + "|" + String.valueOf(dto.column())
                + "|" + String.valueOf(dto.message());
    }

    private static List<ValidationIssueDto> issuesFromEntity(EvaluationDatasetEntity e) {
        List<ValidationIssuePayload> payloads = e.getValidationIssues();
        if (payloads == null || payloads.isEmpty()) {
            return List.of();
        }
        return payloads.stream().map(ValidationIssueDto::from).toList();
    }

    private static String sanitizeFilename(String name) {
        return name.replace("..", "_").replace("/", "_").replace("\\", "_");
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}

package com.uniovi.rag.application.service.evaluation.corpus;

import com.uniovi.rag.domain.ProjectDocumentStatus;
import com.uniovi.rag.domain.evaluation.EvaluationCorpusSourceType;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.infrastructure.persistence.EvaluationCorpusDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationCorpusRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.ProjectRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationCorpusEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.application.port.BinaryStoragePort;
import com.uniovi.rag.application.service.knowledge.KnowledgeIngestionService;
import com.uniovi.rag.application.service.project.ProjectAccessService;
import com.uniovi.rag.interfaces.rest.dto.ProjectDocumentDto;
import com.uniovi.rag.interfaces.rest.dto.evaluation.EvaluationCorpusAttachFromProjectRequest;
import com.uniovi.rag.interfaces.rest.dto.evaluation.EvaluationCorpusCreateRequest;
import com.uniovi.rag.interfaces.rest.dto.evaluation.EvaluationCorpusSummaryDto;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EvaluationCorpusApplicationServiceTest {

    @Mock private EvaluationCorpusRepository evaluationCorpusRepository;
    @Mock private EvaluationCorpusDocumentRepository evaluationCorpusDocumentRepository;
    @Mock private KnowledgeDocumentRepository knowledgeDocumentRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProjectAccessService projectAccessService;
    @Mock private KnowledgeIngestionService knowledgeIngestionService;
    @Mock private BinaryStoragePort binaryStoragePort;

    private EvaluationCorpusApplicationService service;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service =
                new EvaluationCorpusApplicationService(
                        evaluationCorpusRepository,
                        evaluationCorpusDocumentRepository,
                        knowledgeDocumentRepository,
                        projectRepository,
                        userRepository,
                        projectAccessService,
                        knowledgeIngestionService,
                        binaryStoragePort);
    }

    @Test
    void createPersistsCorpusWithSandboxProject() {
        UserEntity owner = mock(UserEntity.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(owner));
        ProjectEntity indexProject = mock(ProjectEntity.class);
        UUID indexProjectId = UUID.randomUUID();
        when(indexProject.getId()).thenReturn(indexProjectId);
        when(projectRepository.save(any(ProjectEntity.class))).thenReturn(indexProject);
        EvaluationCorpusEntity saved = mock(EvaluationCorpusEntity.class);
        UUID corpusId = UUID.randomUUID();
        when(saved.getId()).thenReturn(corpusId);
        when(saved.getName()).thenReturn("Lab evaluation corpus");
        when(saved.getSourceType()).thenReturn(EvaluationCorpusSourceType.UPLOADED);
        when(saved.getIndexProject()).thenReturn(indexProject);
        when(saved.getCreatedAt()).thenReturn(Instant.now());
        when(saved.getUpdatedAt()).thenReturn(Instant.now());
        when(evaluationCorpusRepository.save(any(EvaluationCorpusEntity.class))).thenReturn(saved);
        when(evaluationCorpusDocumentRepository.findDocumentsByCorpusId(corpusId)).thenReturn(List.of());

        EvaluationCorpusSummaryDto summary =
                service.create(userId, new EvaluationCorpusCreateRequest("My corpus"));

        assertThat(summary.id()).isEqualTo(corpusId);
        assertThat(summary.documentCount()).isZero();
        verify(projectRepository).save(any(ProjectEntity.class));
    }

    @Test
    void requireContextReturnsLinkedDocuments() {
        UUID corpusId = UUID.randomUUID();
        UUID indexProjectId = UUID.randomUUID();
        EvaluationCorpusEntity corpus = mock(EvaluationCorpusEntity.class);
        ProjectEntity indexProject = mock(ProjectEntity.class);
        when(indexProject.getId()).thenReturn(indexProjectId);
        when(corpus.getIndexProject()).thenReturn(indexProject);
        when(evaluationCorpusRepository.findByIdAndOwner_Id(corpusId, userId)).thenReturn(Optional.of(corpus));

        UUID docId = UUID.randomUUID();
        KnowledgeDocumentEntity doc = mock(KnowledgeDocumentEntity.class);
        when(doc.getId()).thenReturn(docId);
        when(doc.getStatus()).thenReturn(ProjectDocumentStatus.READY);
        when(doc.getStorageUri()).thenReturn("uri");
        when(evaluationCorpusDocumentRepository.findDocumentsByCorpusId(corpusId)).thenReturn(List.of(doc));

        var context = service.requireContext(userId, corpusId);
        assertThat(context.corpusId()).isEqualTo(corpusId);
        assertThat(context.indexProjectId()).isEqualTo(indexProjectId);
        assertThat(context.readyDocumentIds()).containsExactly(docId);
    }

    @Test
    void uploadDocuments_linksReadyFile_andReportsFailedFile() throws Exception {
        UUID corpusId = UUID.randomUUID();
        UUID indexProjectId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        EvaluationCorpusEntity corpus = mock(EvaluationCorpusEntity.class);
        ProjectEntity indexProject = mock(ProjectEntity.class);
        when(indexProject.getId()).thenReturn(indexProjectId);
        when(corpus.getId()).thenReturn(corpusId);
        when(corpus.getIndexProject()).thenReturn(indexProject);
        when(corpus.getSourceType()).thenReturn(EvaluationCorpusSourceType.UPLOADED);
        when(evaluationCorpusRepository.findByIdAndOwner_Id(corpusId, userId)).thenReturn(Optional.of(corpus));
        when(evaluationCorpusRepository.save(corpus)).thenReturn(corpus);

        MultipartFile okFile = mock(MultipartFile.class);
        when(okFile.isEmpty()).thenReturn(false);
        when(okFile.getOriginalFilename()).thenReturn("ok.pdf");
        MultipartFile badFile = mock(MultipartFile.class);
        when(badFile.isEmpty()).thenReturn(false);
        when(badFile.getOriginalFilename()).thenReturn("bad.pdf");

        ProjectDocumentDto uploaded =
                new ProjectDocumentDto(
                        docId,
                        "ok.pdf",
                        ProjectDocumentStatus.INGESTING,
                        null,
                        null,
                        Instant.now(),
                        null,
                        CorpusScope.PROJECT_SHARED,
                        null,
                        null,
                        null,
                        true);
        when(knowledgeIngestionService.ingestProjectSharedDocumentSynchronouslyFromBytes(
                        eq(userId), eq(indexProjectId), any(), eq("ok.pdf"), any()))
                .thenReturn(uploaded);
        when(knowledgeIngestionService.ingestProjectSharedDocumentSynchronouslyFromBytes(
                        eq(userId), eq(indexProjectId), any(), eq("bad.pdf"), any()))
                .thenThrow(new IllegalStateException("unsupported"));

        KnowledgeDocumentEntity linked = mock(KnowledgeDocumentEntity.class);
        when(linked.getStatus()).thenReturn(ProjectDocumentStatus.INGESTING);
        when(evaluationCorpusDocumentRepository.findDocumentsByCorpusId(corpusId)).thenReturn(List.of(linked));

        var response = service.uploadDocuments(userId, corpusId, List.of(okFile, badFile));

        assertThat(response.uploads()).hasSize(2);
        assertThat(response.uploads().get(0).status()).isEqualTo("PROCESSING");
        assertThat(response.uploads().get(1).status()).isEqualTo("FAILED");
        verify(evaluationCorpusDocumentRepository).save(any());
    }

    @Test
    void uploadDocuments_mapsRuntimeExceptionToFailedUploadItem() throws Exception {
        UUID corpusId = UUID.randomUUID();
        UUID indexProjectId = UUID.randomUUID();
        EvaluationCorpusEntity corpus = mock(EvaluationCorpusEntity.class);
        ProjectEntity indexProject = mock(ProjectEntity.class);
        when(indexProject.getId()).thenReturn(indexProjectId);
        when(corpus.getId()).thenReturn(corpusId);
        when(corpus.getIndexProject()).thenReturn(indexProject);
        when(corpus.getSourceType()).thenReturn(EvaluationCorpusSourceType.UPLOADED);
        when(evaluationCorpusRepository.findByIdAndOwner_Id(corpusId, userId)).thenReturn(Optional.of(corpus));
        when(evaluationCorpusRepository.save(corpus)).thenReturn(corpus);

        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("bad.pdf");
        when(knowledgeIngestionService.ingestProjectSharedDocumentSynchronouslyFromBytes(
                        eq(userId), eq(indexProjectId), any(), eq("bad.pdf"), any()))
                .thenThrow(new IllegalStateException("ingest failed"));

        var response = service.uploadDocuments(userId, corpusId, List.of(file));

        assertThat(response.uploads()).hasSize(1);
        assertThat(response.uploads().get(0).status()).isEqualTo("FAILED");
        assertThat(response.uploads().get(0).error()).contains("ingest failed");
    }

    @Test
    void uploadDocuments_requiresAtLeastOneFile() {
        UUID corpusId = UUID.randomUUID();
        EvaluationCorpusEntity corpus = mock(EvaluationCorpusEntity.class);
        when(evaluationCorpusRepository.findByIdAndOwner_Id(corpusId, userId)).thenReturn(Optional.of(corpus));

        assertThatThrownBy(() -> service.uploadDocuments(userId, corpusId, List.of()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void requireReadyContext_rejectsEmptyCorpus() {
        UUID corpusId = UUID.randomUUID();
        EvaluationCorpusEntity corpus = mock(EvaluationCorpusEntity.class);
        when(evaluationCorpusRepository.findByIdAndOwner_Id(corpusId, userId)).thenReturn(Optional.of(corpus));
        when(evaluationCorpusDocumentRepository.findDocumentsByCorpusId(corpusId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.requireReadyContext(userId, corpusId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex ->
                                assertThat(((ResponseStatusException) ex).getReason())
                                        .isEqualTo(EvaluationCorpusApplicationService.KB_EMPTY));
    }

    @Test
    void requireReadyContext_acceptsReadyDocument() {
        UUID corpusId = UUID.randomUUID();
        EvaluationCorpusEntity corpus = mock(EvaluationCorpusEntity.class);
        when(evaluationCorpusRepository.findByIdAndOwner_Id(corpusId, userId)).thenReturn(Optional.of(corpus));
        KnowledgeDocumentEntity doc = mock(KnowledgeDocumentEntity.class);
        when(doc.getId()).thenReturn(UUID.randomUUID());
        when(doc.getStatus()).thenReturn(ProjectDocumentStatus.READY);
        when(doc.getStorageUri()).thenReturn("storage://doc");
        when(evaluationCorpusDocumentRepository.findDocumentsByCorpusId(corpusId)).thenReturn(List.of(doc));

        var context = service.requireReadyContext(userId, corpusId);
        assertThat(context.readyDocumentIds()).hasSize(1);
    }

    @Test
    void attachFromProjectLinksSharedDocumentInSameIndexProject() {
        UUID corpusId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        EvaluationCorpusEntity corpus = mock(EvaluationCorpusEntity.class);
        ProjectEntity indexProject = mock(ProjectEntity.class);
        when(indexProject.getId()).thenReturn(projectId);
        when(corpus.getId()).thenReturn(corpusId);
        when(corpus.getIndexProject()).thenReturn(indexProject);
        when(corpus.getSourceType()).thenReturn(EvaluationCorpusSourceType.UPLOADED);
        when(evaluationCorpusRepository.findByIdAndOwner_Id(corpusId, userId)).thenReturn(Optional.of(corpus));
        when(evaluationCorpusRepository.save(corpus)).thenReturn(corpus);

        KnowledgeDocumentEntity source = mock(KnowledgeDocumentEntity.class);
        when(source.getId()).thenReturn(docId);
        when(source.getCorpusScope()).thenReturn(CorpusScope.PROJECT_SHARED);
        ProjectEntity sourceProject = mock(ProjectEntity.class);
        when(sourceProject.getId()).thenReturn(projectId);
        when(source.getProject()).thenReturn(sourceProject);
        when(knowledgeDocumentRepository.findByIdAndProject_Id(docId, projectId)).thenReturn(Optional.of(source));
        when(evaluationCorpusDocumentRepository.existsByCorpusIdAndDocumentId(corpusId, docId)).thenReturn(false);
        when(evaluationCorpusDocumentRepository.findDocumentsByCorpusId(corpusId)).thenReturn(List.of(source));

        var summary =
                service.attachFromProject(
                        userId,
                        corpusId,
                        new EvaluationCorpusAttachFromProjectRequest(projectId, List.of(docId)));

        assertThat(summary.documentCount()).isEqualTo(1);
        verify(evaluationCorpusDocumentRepository).save(any());
    }
}

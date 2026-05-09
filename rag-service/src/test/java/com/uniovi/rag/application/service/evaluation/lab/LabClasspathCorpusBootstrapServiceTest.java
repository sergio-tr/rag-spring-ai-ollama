package com.uniovi.rag.application.service.evaluation.lab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.service.knowledge.KnowledgeIngestionService;
import com.uniovi.rag.domain.ProjectDocumentStatus;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.interfaces.rest.dto.ProjectDocumentDto;
import com.uniovi.rag.service.project.ProjectAccessService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

@ExtendWith(MockitoExtension.class)
class LabClasspathCorpusBootstrapServiceTest {

    @Mock
    private KnowledgeIngestionService knowledgeIngestionService;

    @Mock
    private KnowledgeDocumentRepository knowledgeDocumentRepository;

    @Mock
    private ProjectAccessService projectAccessService;

    private ResourcePatternResolver resourceResolver;

    private LabClasspathCorpusBootstrapService service;

    private UUID userId;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        resourceResolver = mock(ResourcePatternResolver.class);
        service = new LabClasspathCorpusBootstrapService(
                knowledgeIngestionService, knowledgeDocumentRepository, projectAccessService, resourceResolver);
    }

    @Test
    void bootstrap_disabled_returnsSummaryWithoutIngest() throws Exception {
        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setAggregatesJson(Map.of("corpusBootstrapPolicy", Map.of("enabled", false)));

        LabCorpusBootstrapResult r = service.bootstrap(userId, run);

        assertThat(r.enabled()).isFalse();
        assertThat(r.discoveredCount()).isZero();
    }

    @Test
    void bootstrap_requiresProjectWhenEnabled() {
        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setAggregatesJson(Map.of("corpusBootstrapPolicy", Map.of("enabled", true)));

        assertThatThrownBy(() -> service.bootstrap(userId, run))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(LabCorpusBootstrapErrors.REQUIRES_PROJECT);
    }

    @Test
    void bootstrap_throwsWhenNoClasspathFilesMatch() throws Exception {
        EvaluationRunEntity run = runWithProjectAndPolicy();
        when(resourceResolver.getResources("classpath*:docs/**/*")).thenReturn(new Resource[0]);

        assertThatThrownBy(() -> service.bootstrap(userId, run))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(LabCorpusBootstrapErrors.NO_DOCUMENTS)
                .hasMessageContaining("src/main/resources/docs");
    }

    @Test
    void filterCorpusResources_drops_gitkeep_and_readme() {
        List<Resource> raw = new ArrayList<>();
        raw.add(fileResource("keep.pdf", "x".getBytes(StandardCharsets.UTF_8)));
        raw.add(fileResource(".gitkeep", "\n".getBytes(StandardCharsets.UTF_8)));
        raw.add(fileResource("README.md", "# x".getBytes(StandardCharsets.UTF_8)));
        assertThat(LabClasspathCorpusBootstrapService.filterCorpusResources(raw)).hasSize(1);
    }

    @Test
    void bootstrap_failOnDocumentError_false_continues_after_single_failure() throws Exception {
        EvaluationRunEntity run = runWithProjectAndPolicy();
        @SuppressWarnings("unchecked")
        Map<String, Object> inner =
                new LinkedHashMap<>(
                        (Map<String, Object>)
                                run.getAggregatesJson().get("corpusBootstrapPolicy"));
        inner.put("failOnDocumentError", false);
        run.setAggregatesJson(Map.of("corpusBootstrapPolicy", inner));

        Resource bad = fileResource("bad.pdf", "%PDF broken".getBytes(StandardCharsets.UTF_8));
        Resource good = fileResource("good.pdf", "%PDF ok".getBytes(StandardCharsets.UTF_8));
        when(resourceResolver.getResources("classpath*:docs/**/*")).thenReturn(new Resource[] {bad, good});

        UUID goodId = UUID.randomUUID();
        when(knowledgeIngestionService.ingestProjectSharedDocumentSynchronouslyFromBytes(
                        eq(userId), eq(projectId), any(), eq("bad.pdf"), eq("application/pdf")))
                .thenThrow(new IllegalStateException("simulated ingest failure"));
        when(knowledgeIngestionService.ingestProjectSharedDocumentSynchronouslyFromBytes(
                        eq(userId), eq(projectId), any(), eq("good.pdf"), eq("application/pdf")))
                .thenReturn(
                        new ProjectDocumentDto(
                                goodId, null, null, null, null, null, null, null, null, null, null, false));

        KnowledgeDocumentEntity doc = Mockito.mock(KnowledgeDocumentEntity.class);
        Mockito.when(doc.getStatus()).thenReturn(ProjectDocumentStatus.READY);
        Mockito.when(doc.getStorageUri()).thenReturn("mem://good");
        when(knowledgeDocumentRepository.findByIdAndProject_Id(eq(goodId), eq(projectId)))
                .thenReturn(Optional.of(doc));

        LabCorpusBootstrapResult result = service.bootstrap(userId, run);

        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.readyCount()).isEqualTo(1);
        assertThat(result.errors()).hasSize(1);
    }

    @Test
    void bootstrap_failOnDocumentError_true_fails_on_ingest_exception() throws Exception {
        EvaluationRunEntity run = runWithProjectAndPolicy();
        Resource one = fileResource("only.pdf", "%PDF".getBytes(StandardCharsets.UTF_8));
        when(resourceResolver.getResources("classpath*:docs/**/*")).thenReturn(new Resource[] {one});
        when(knowledgeIngestionService.ingestProjectSharedDocumentSynchronouslyFromBytes(
                        eq(userId), eq(projectId), any(), eq("only.pdf"), eq("application/pdf")))
                .thenThrow(new IllegalStateException("simulated"));

        assertThatThrownBy(() -> service.bootstrap(userId, run))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LAB_CORPUS_BOOTSTRAP_DOCUMENT_FAILED");
    }

    @Test
    void toAggregatesMap_exposes_audit_keys() {
        LabCorpusBootstrapResult r =
                new LabCorpusBootstrapResult(
                        true,
                        "classpath*:docs/**/*",
                        "PROJECT_SHARED",
                        2,
                        1,
                        1,
                        2,
                        0,
                        0,
                        List.of(),
                        List.of(),
                        null,
                        null);
        Map<String, Object> m = r.toAggregatesMap();
        assertThat(m.get("bootstrapDocumentsFound")).isEqualTo(2);
        assertThat(m.get("bootstrapDocumentsCreated")).isEqualTo(1);
        assertThat(m.get("classpathDocsLocation")).isEqualTo("classpath*:docs/**/*");
        assertThat(m.get("corpusScope")).isEqualTo("PROJECT_SHARED");
    }

    @Test
    void bootstrap_ingestsEachFileAsProjectShared() throws Exception {
        EvaluationRunEntity run = runWithProjectAndPolicy();
        Resource r1 = fileResource("acta-one.pdf", "%PDF-1 mock".getBytes(StandardCharsets.UTF_8));
        Resource r2 = fileResource("acta-two.txt", "hello".getBytes(StandardCharsets.UTF_8));
        when(resourceResolver.getResources("classpath*:docs/**/*")).thenReturn(new Resource[] {r1, r2});

        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(knowledgeIngestionService.ingestProjectSharedDocumentSynchronouslyFromBytes(
                        eq(userId), eq(projectId), any(), eq("acta-one.pdf"), eq("application/pdf")))
                .thenReturn(
                        new ProjectDocumentDto(
                                id1, null, null, null, null, null, null, null, null, null, null, false));
        when(knowledgeIngestionService.ingestProjectSharedDocumentSynchronouslyFromBytes(
                        eq(userId), eq(projectId), any(), eq("acta-two.txt"), eq("text/plain")))
                .thenReturn(
                        new ProjectDocumentDto(
                                id2, null, null, null, null, null, null, null, null, null, null, false));

        KnowledgeDocumentEntity doc1 = Mockito.mock(KnowledgeDocumentEntity.class);
        Mockito.when(doc1.getStatus()).thenReturn(ProjectDocumentStatus.READY);
        Mockito.when(doc1.getStorageUri()).thenReturn("s3://bucket/a");
        when(knowledgeDocumentRepository.findByIdAndProject_Id(eq(id1), eq(projectId)))
                .thenReturn(Optional.of(doc1));

        KnowledgeDocumentEntity doc2 = Mockito.mock(KnowledgeDocumentEntity.class);
        Mockito.when(doc2.getStatus()).thenReturn(ProjectDocumentStatus.READY);
        Mockito.when(doc2.getStorageUri()).thenReturn("s3://bucket/b");
        when(knowledgeDocumentRepository.findByIdAndProject_Id(eq(id2), eq(projectId)))
                .thenReturn(Optional.of(doc2));

        LabCorpusBootstrapResult result = service.bootstrap(userId, run);

        assertThat(result.enabled()).isTrue();
        assertThat(result.discoveredCount()).isEqualTo(2);
        assertThat(result.createdCount()).isEqualTo(2);
        assertThat(result.readyCount()).isEqualTo(2);
        assertThat(result.documentIds()).containsExactly(id1, id2);
    }

    @Test
    void bootstrap_skipExisting_reusesReadyRowWithMatchingChecksum() throws Exception {
        EvaluationRunEntity run = runWithProjectAndPolicy();
        Resource r1 = fileResource("dup.txt", "same".getBytes(StandardCharsets.UTF_8));
        when(resourceResolver.getResources("classpath*:docs/**/*")).thenReturn(new Resource[] {r1});

        UUID existingId = UUID.randomUUID();
        KnowledgeDocumentEntity row = Mockito.mock(KnowledgeDocumentEntity.class);
        Mockito.when(row.getId()).thenReturn(existingId);
        Mockito.when(row.getStatus()).thenReturn(ProjectDocumentStatus.READY);
        Mockito.when(row.getStorageUri()).thenReturn("mem://x");
        Mockito.when(row.getContentChecksum()).thenReturn(null);
        when(knowledgeDocumentRepository.findFirstByProject_IdAndFileNameAndCorpusScopeAndConversationIsNull(
                        eq(projectId), eq("dup.txt"), eq(CorpusScope.PROJECT_SHARED)))
                .thenReturn(Optional.of(row));
        when(knowledgeDocumentRepository.findByIdAndProject_Id(eq(existingId), eq(projectId)))
                .thenReturn(Optional.of(row));

        LabCorpusBootstrapResult result = service.bootstrap(userId, run);

        assertThat(result.reusedCount()).isEqualTo(1);
        assertThat(result.createdCount()).isZero();
        assertThat(result.documentIds()).containsExactly(existingId);
    }

    private EvaluationRunEntity runWithProjectAndPolicy() {
        EvaluationRunEntity run = new EvaluationRunEntity();
        var p = Mockito.mock(ProjectEntity.class);
        Mockito.when(p.getId()).thenReturn(projectId);
        run.setProject(p);
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("enabled", true);
        policy.put("classpathDocsLocation", "classpath*:docs/**/*");
        policy.put("corpusScope", "PROJECT_SHARED");
        policy.put("skipExisting", true);
        policy.put("failOnDocumentError", true);
        run.setAggregatesJson(Map.of("corpusBootstrapPolicy", policy));
        when(projectAccessService.requireOwnedProject(userId, projectId)).thenReturn(p);
        return run;
    }

    private static Resource fileResource(String filename, byte[] bytes) {
        return new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }

            @Override
            public InputStream getInputStream() {
                return new ByteArrayInputStream(bytes);
            }
        };
    }
}

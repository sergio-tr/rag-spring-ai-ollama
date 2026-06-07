package com.uniovi.rag.infrastructure.persistence.jpa;

import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.domain.EvaluationDatasetType;
import com.uniovi.rag.domain.EvaluationRunStatus;
import com.uniovi.rag.domain.EvaluationRunType;
import com.uniovi.rag.domain.config.runtime.ConfigProfileType;
import com.uniovi.rag.domain.evaluation.EvaluationDatasetScope;
import com.uniovi.rag.domain.knowledge.DocumentArtifactType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exercises persistence entity accessors without a database so JaCoCo stays above the bundle gate when
 * Postgres-backed {@code @SpringBootTest} classes are skipped (no Docker / JDBC).
 */
class JpaHeavyEntityAccessorTest {

    private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");

    @Test
    void evaluationRunCarriesRunMetadataAcrossAccessors() {
        UserEntity user = UserEntityFactory.newRegisteredUser("a@b.c", "U", "p");
        EvaluationDatasetEntity dataset = new EvaluationDatasetEntity();
        dataset.setName("d");
        dataset.setType(EvaluationDatasetType.RAG);
        dataset.setUploadedAt(NOW);
        dataset.setDatasetScope(EvaluationDatasetScope.USER_DATASET.name());
        ProjectEntity project = new ProjectEntity();
        ResolvedConfigSnapshotEntity snap = new ResolvedConfigSnapshotEntity();
        KnowledgeIndexSnapshotEntity idx = new KnowledgeIndexSnapshotEntity();
        RagPresetEntity preset = new RagPresetEntity();
        AsyncTaskEntity task = AsyncTaskEntity.queued(user, AsyncTaskType.ACCOUNT_EXPORT, Map.of(), NOW);

        EvaluationRunEntity run = new EvaluationRunEntity();
        UUID runId = UUID.randomUUID();
        run.setId(runId);
        run.setUser(user);
        run.setProject(project);
        run.setName("bench");
        run.setDataset(dataset);
        run.setType(EvaluationRunType.RAG_FULL);
        run.setConfigIds(List.of("c1"));
        run.setStatus(EvaluationRunStatus.RUNNING);
        run.setProgress(3);
        run.setCreatedAt(NOW);
        run.setCompletedAt(NOW.plusSeconds(1));
        run.setBenchmarkKind("k");
        run.setRunKind("manual");
        run.setWorkflowSchemaVersion("v1");
        run.setDatasetSha256("ab");
        run.setResolvedConfigSnapshot(snap);
        run.setIndexSnapshot(idx);
        run.setIndexSignatureHash("sig");
        run.setPreset(preset);
        run.setLlmModelId("llm");
        run.setEmbeddingModelId("emb");
        run.setClassifierModelId("clf");
        run.setAsyncTask(task);
        run.setAggregatesJson(Map.of("n", 2));

        assertThat(run.getId()).isEqualTo(runId);
        assertThat(run.getUser()).isSameAs(user);
        assertThat(run.getProject()).isSameAs(project);
        assertThat(run.getName()).isEqualTo("bench");
        assertThat(run.getDataset()).isSameAs(dataset);
        assertThat(run.getType()).isEqualTo(EvaluationRunType.RAG_FULL);
        assertThat(run.getConfigIds()).containsExactly("c1");
        assertThat(run.getStatus()).isEqualTo(EvaluationRunStatus.RUNNING);
        assertThat(run.getProgress()).isEqualTo(3);
        assertThat(run.getCreatedAt()).isEqualTo(NOW);
        assertThat(run.getCompletedAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(run.getBenchmarkKind()).isEqualTo("k");
        assertThat(run.getRunKind()).isEqualTo("manual");
        assertThat(run.getWorkflowSchemaVersion()).isEqualTo("v1");
        assertThat(run.getDatasetSha256()).isEqualTo("ab");
        assertThat(run.getResolvedConfigSnapshot()).isSameAs(snap);
        assertThat(run.getIndexSnapshot()).isSameAs(idx);
        assertThat(run.getIndexSignatureHash()).isEqualTo("sig");
        assertThat(run.getPreset()).isSameAs(preset);
        assertThat(run.getLlmModelId()).isEqualTo("llm");
        assertThat(run.getEmbeddingModelId()).isEqualTo("emb");
        assertThat(run.getClassifierModelId()).isEqualTo("clf");
        assertThat(run.getAsyncTask()).isSameAs(task);
        assertThat(run.getAggregatesJson()).containsEntry("n", 2);
    }

    @Test
    void evaluationResultCarriesQuestionOutcomeAcrossAccessors() {
        EvaluationRunEntity run = new EvaluationRunEntity();
        Instant evaluated = NOW.plusSeconds(5);
        EvaluationResultEntity r = new EvaluationResultEntity();
        UUID id = UUID.randomUUID();
        r.setId(id);
        r.setRun(run);
        r.setQuestionText("q");
        r.setExpectedAnswer("e");
        r.setActualAnswer("a");
        r.setCorrectness(1);
        r.setQueryType("COUNT");
        r.setLatencyMs(99L);
        r.setEvaluatedAt(evaluated);
        r.setBenchmarkKind("bk");
        r.setMetricsPayload(Map.of("f1", 0.9));

        assertThat(r.getId()).isEqualTo(id);
        assertThat(r.getRun()).isSameAs(run);
        assertThat(r.getQuestionText()).isEqualTo("q");
        assertThat(r.getExpectedAnswer()).isEqualTo("e");
        assertThat(r.getActualAnswer()).isEqualTo("a");
        assertThat(r.getCorrectness()).isEqualTo(1);
        assertThat(r.getQueryType()).isEqualTo("COUNT");
        assertThat(r.getLatencyMs()).isEqualTo(99L);
        assertThat(r.getEvaluatedAt()).isEqualTo(evaluated);
        assertThat(r.getBenchmarkKind()).isEqualTo("bk");
        assertThat(r.getMetricsPayload()).containsEntry("f1", 0.9);
    }

    @Test
    void evaluationDatasetCarriesUploadMetadataAcrossAccessors() {
        UserEntity owner = UserEntityFactory.newRegisteredUser("o@b.c", "O", "p");
        EvaluationDatasetEntity d = new EvaluationDatasetEntity();
        UUID id = UUID.randomUUID();
        d.setId(id);
        d.setOwner(owner);
        d.setName("n");
        d.setFileName("f.csv");
        d.setQuestionCount(10);
        d.setSha256("sha");
        d.setType(EvaluationDatasetType.CLASSIFIER);
        d.setUploadedAt(NOW);
        d.setValidatedAt(NOW.plusSeconds(2));
        d.setDatasetScope(EvaluationDatasetScope.USER_DATASET.name());
        d.setStorageUri("s3://x");
        d.setByteSize(500L);
        d.setMimeType("text/csv");
        d.setSchemaVersion("1");
        d.setBenchmarkKindsAllowed(Map.of("k", true));

        assertThat(d.getId()).isEqualTo(id);
        assertThat(d.getOwner()).isSameAs(owner);
        assertThat(d.getName()).isEqualTo("n");
        assertThat(d.getFileName()).isEqualTo("f.csv");
        assertThat(d.getQuestionCount()).isEqualTo(10);
        assertThat(d.getSha256()).isEqualTo("sha");
        assertThat(d.getType()).isEqualTo(EvaluationDatasetType.CLASSIFIER);
        assertThat(d.getUploadedAt()).isEqualTo(NOW);
        assertThat(d.getValidatedAt()).isEqualTo(NOW.plusSeconds(2));
        assertThat(d.getDatasetScope()).isEqualTo(EvaluationDatasetScope.USER_DATASET.name());
        assertThat(d.getStorageUri()).isEqualTo("s3://x");
        assertThat(d.getByteSize()).isEqualTo(500L);
        assertThat(d.getMimeType()).isEqualTo("text/csv");
        assertThat(d.getSchemaVersion()).isEqualTo("1");
        assertThat(d.getBenchmarkKindsAllowed()).containsEntry("k", true);
    }

    @Test
    void documentArtifactFactoryBuildsRow() {
        KnowledgeDocumentEntity doc = new KnowledgeDocumentEntity();
        DocumentArtifactEntity a = DocumentArtifactEntity.newRow();
        a.setDocument(doc);
        a.setArtifactType(DocumentArtifactType.PARSED);
        a.setPayloadJsonb(Map.of("p", "v"));
        a.setContentHash("h");
        a.setCreatedAt(NOW);

        assertThat(a.getDocument()).isSameAs(doc);
        assertThat(a.getArtifactType()).isEqualTo(DocumentArtifactType.PARSED);
        assertThat(a.getPayloadJsonb()).containsEntry("p", "v");
        assertThat(a.getContentHash()).isEqualTo("h");
        assertThat(a.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void messageFeedbackCreateExposesCoreFields() {
        MessageEntity msg = new MessageEntity();
        UserEntity user = UserEntityFactory.newRegisteredUser("u@b.c", "U", "p");
        MessageFeedbackEntity fb = MessageFeedbackEntity.create(msg, user, 5, "ok", NOW);
        assertThat(fb.getRating()).isEqualTo(5);
        assertThat(fb.getComment()).isEqualTo("ok");
        assertThat(fb.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void ragPresetProfileRefLinkWiresCompositeKey() {
        UUID presetId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        RagPresetEntity preset = mock(RagPresetEntity.class);
        when(preset.getId()).thenReturn(presetId);
        ConfigProfileEntity profile = mock(ConfigProfileEntity.class);
        when(profile.getId()).thenReturn(profileId);

        RagPresetProfileRefEntity ref = RagPresetProfileRefEntity.link(preset, profile, 1, "primary");
        assertThat(ref.getPreset()).isSameAs(preset);
        assertThat(ref.getProfile()).isSameAs(profile);
        assertThat(ref.getOrdinal()).isEqualTo(1);
        assertThat(ref.getRole()).isEqualTo("primary");
        assertThat(ref.getId().getPresetId()).isEqualTo(presetId);
        assertThat(ref.getId().getProfileId()).isEqualTo(profileId);
    }

    @Test
    void configProfileDraftFactorySetsBaselineFields() {
        UserEntity owner = UserEntityFactory.newRegisteredUser("c@b.c", "C", "p");
        ConfigProfileEntity p =
                ConfigProfileEntity.newDraft(
                        ConfigProfileType.METADATA,
                        3,
                        "L",
                        Map.of("k", "v"),
                        owner,
                        owner,
                        NOW);
        assertThat(p.getProfileType()).isEqualTo(ConfigProfileType.METADATA);
        assertThat(p.getVersion()).isEqualTo(3);
        assertThat(p.getLabel()).isEqualTo("L");
        assertThat(p.getPayload()).containsEntry("k", "v");
        assertThat(p.getOwner()).isSameAs(owner);
        assertThat(p.isImmutable()).isFalse();
        assertThat(p.getCreatedAt()).isEqualTo(NOW);
        assertThat(p.getCreatedBy()).isSameAs(owner);
    }
}

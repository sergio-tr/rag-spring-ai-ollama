package com.uniovi.rag.infrastructure.persistence;

import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import com.uniovi.rag.domain.AsyncTaskStatus;

public interface EvaluationRunRepository extends JpaRepository<EvaluationRunEntity, UUID> {

    List<EvaluationRunEntity> findByUser_IdOrderByCreatedAtDesc(UUID userId);

    Optional<EvaluationRunEntity> findByIdAndUser_Id(UUID id, UUID userId);

    List<EvaluationRunEntity> findByIdInAndUser_Id(List<UUID> ids, UUID userId);

    Optional<EvaluationRunEntity> findByAsyncTask_Id(UUID asyncTaskId);

    @Query("""
            SELECT r FROM EvaluationRunEntity r
            JOIN FETCH r.asyncTask t
            JOIN FETCH r.dataset d
            LEFT JOIN FETCH r.project p
            WHERE r.user.id = :userId
              AND t.status IN :statuses
            ORDER BY r.createdAt DESC
            """)
    List<EvaluationRunEntity> findActiveRunsByUser(
            @Param("userId") UUID userId,
            @Param("statuses") List<AsyncTaskStatus> statuses);

    @Query("""
            SELECT r FROM EvaluationRunEntity r
            JOIN FETCH r.asyncTask t
            JOIN FETCH r.dataset d
            LEFT JOIN FETCH r.project p
            WHERE r.user.id = :userId
              AND t.status IN :statuses
              AND (
                   :projectId IS NULL
                   OR p.id = :projectId
                   OR p.id IS NULL
              )
            ORDER BY r.createdAt DESC
            """)
    List<EvaluationRunEntity> findActiveRunsByUserAndProjectScope(
            @Param("userId") UUID userId,
            @Param("projectId") UUID projectId,
            @Param("statuses") List<AsyncTaskStatus> statuses);

    @Query("""
            SELECT r FROM EvaluationRunEntity r
            JOIN FETCH r.dataset
            JOIN FETCH r.user
            LEFT JOIN FETCH r.project
            WHERE r.id = :id
            """)
    Optional<EvaluationRunEntity> findByIdFetchDataset(@Param("id") UUID id);

    @Query("""
            SELECT r FROM EvaluationRunEntity r
            JOIN FETCH r.dataset
            JOIN FETCH r.user
            LEFT JOIN FETCH r.project
            LEFT JOIN FETCH r.evaluationCorpus c
            LEFT JOIN FETCH c.indexProject
            WHERE r.id = :id
            """)
    Optional<EvaluationRunEntity> findByIdFetchDatasetAndCorpus(@Param("id") UUID id);

    @Query("""
            SELECT COALESCE(p.id, ip.id) FROM EvaluationRunEntity r
            LEFT JOIN r.project p
            LEFT JOIN r.evaluationCorpus c
            LEFT JOIN c.indexProject ip
            WHERE r.id = :runId
            """)
    Optional<UUID> findEffectiveProjectIdByRunId(@Param("runId") UUID runId);

    @Query("SELECT c.id FROM EvaluationRunEntity r JOIN r.evaluationCorpus c WHERE r.id = :runId")
    Optional<UUID> findCorpusIdByRunId(@Param("runId") UUID runId);

    @Query("SELECT r.user.id FROM EvaluationRunEntity r WHERE r.id = :runId")
    Optional<UUID> findUserIdByRunId(@Param("runId") UUID runId);

    @Query("SELECT r.dataset.id FROM EvaluationRunEntity r WHERE r.id = :runId")
    Optional<UUID> findDatasetIdByRunId(@Param("runId") UUID runId);

    @Query("SELECT r.dataset.experimentalKind FROM EvaluationRunEntity r WHERE r.id = :runId")
    Optional<String> findDatasetExperimentalKindByRunId(@Param("runId") UUID runId);

    @Query("SELECT p.id FROM EvaluationRunEntity r LEFT JOIN r.project p WHERE r.id = :runId AND p.id IS NOT NULL")
    Optional<UUID> findProjectIdByRunId(@Param("runId") UUID runId);

    @Query("SELECT r.aggregatesJson FROM EvaluationRunEntity r WHERE r.id = :runId")
    Optional<Map<String, Object>> findAggregatesJsonByRunId(@Param("runId") UUID runId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE EvaluationRunEntity r SET r.aggregatesJson = :aggregatesJson WHERE r.id = :runId")
    void updateAggregatesJson(
            @Param("runId") UUID runId, @Param("aggregatesJson") Map<String, Object> aggregatesJson);

    List<EvaluationRunEntity> findByCampaign_IdOrderByCreatedAtAsc(UUID campaignId);

    @Query("SELECT r.campaign.id FROM EvaluationRunEntity r WHERE r.id = :runId")
    Optional<UUID> findCampaignIdByRunId(@Param("runId") UUID runId);

    @Query(
            "SELECT r FROM EvaluationRunEntity r WHERE r.campaign.id = :campaignId AND r.user.id = :userId ORDER BY r.createdAt ASC")
    List<EvaluationRunEntity> findByCampaignIdAndUserId(@Param("campaignId") UUID campaignId, @Param("userId") UUID userId);

    boolean existsByLlmModelId(String llmModelId);

    boolean existsByEmbeddingModelId(String embeddingModelId);
}

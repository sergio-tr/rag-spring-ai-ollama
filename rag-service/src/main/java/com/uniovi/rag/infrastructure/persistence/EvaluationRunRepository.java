package com.uniovi.rag.infrastructure.persistence;

import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EvaluationRunRepository extends JpaRepository<EvaluationRunEntity, UUID> {

    Optional<EvaluationRunEntity> findByIdAndUser_Id(UUID id, UUID userId);

    List<EvaluationRunEntity> findByIdInAndUser_Id(List<UUID> ids, UUID userId);

    Optional<EvaluationRunEntity> findByAsyncTask_Id(UUID asyncTaskId);

    @Query("SELECT r FROM EvaluationRunEntity r JOIN FETCH r.dataset WHERE r.id = :id")
    Optional<EvaluationRunEntity> findByIdFetchDataset(@Param("id") UUID id);

    List<EvaluationRunEntity> findByCampaign_IdOrderByCreatedAtAsc(UUID campaignId);

    @Query(
            "SELECT r FROM EvaluationRunEntity r WHERE r.campaign.id = :campaignId AND r.user.id = :userId ORDER BY r.createdAt ASC")
    List<EvaluationRunEntity> findByCampaignIdAndUserId(@Param("campaignId") UUID campaignId, @Param("userId") UUID userId);
}

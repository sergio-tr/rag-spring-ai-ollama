package com.uniovi.rag.infrastructure.persistence;

import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationCampaignEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EvaluationCampaignRepository extends JpaRepository<EvaluationCampaignEntity, UUID> {
    Optional<EvaluationCampaignEntity> findByIdAndUser_Id(UUID id, UUID userId);
}


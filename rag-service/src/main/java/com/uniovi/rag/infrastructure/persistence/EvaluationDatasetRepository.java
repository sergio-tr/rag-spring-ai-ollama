package com.uniovi.rag.infrastructure.persistence;

import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationDatasetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EvaluationDatasetRepository extends JpaRepository<EvaluationDatasetEntity, UUID> {

    List<EvaluationDatasetEntity> findByOwner_IdOrderByUploadedAtDesc(UUID ownerId);
}

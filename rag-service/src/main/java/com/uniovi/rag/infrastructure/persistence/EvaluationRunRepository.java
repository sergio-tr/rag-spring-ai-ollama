package com.uniovi.rag.infrastructure.persistence;

import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EvaluationRunRepository extends JpaRepository<EvaluationRunEntity, UUID> {

    Optional<EvaluationRunEntity> findByIdAndUser_Id(UUID id, UUID userId);

    Optional<EvaluationRunEntity> findByAsyncTask_Id(UUID asyncTaskId);
}

package com.uniovi.rag.infrastructure.persistence;

import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EvaluationResultRepository extends JpaRepository<EvaluationResultEntity, UUID> {

    List<EvaluationResultEntity> findByRun_IdOrderByEvaluatedAtAsc(UUID runId);

    List<EvaluationResultEntity> findByRun_IdInOrderByEvaluatedAtAsc(List<UUID> runIds);
}

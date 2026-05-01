package com.uniovi.rag.infrastructure.persistence;

import com.uniovi.rag.infrastructure.persistence.jpa.ScheduledEvaluationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ScheduledEvaluationRepository extends JpaRepository<ScheduledEvaluationEntity, UUID> {
}

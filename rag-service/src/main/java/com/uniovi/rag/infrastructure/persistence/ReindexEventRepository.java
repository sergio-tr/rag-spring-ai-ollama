package com.uniovi.rag.infrastructure.persistence;

import com.uniovi.rag.infrastructure.persistence.jpa.ReindexEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReindexEventRepository extends JpaRepository<ReindexEventEntity, UUID> {}

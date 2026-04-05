package com.uniovi.rag.infrastructure.persistence;

import com.uniovi.rag.infrastructure.persistence.jpa.ResolvedConfigSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ResolvedConfigSnapshotRepository extends JpaRepository<ResolvedConfigSnapshotEntity, UUID> {
}

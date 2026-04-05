package com.uniovi.rag.infrastructure.persistence;

import com.uniovi.rag.infrastructure.persistence.jpa.RagPresetProfileRefEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.RagPresetProfileRefId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RagPresetProfileRefRepository extends JpaRepository<RagPresetProfileRefEntity, RagPresetProfileRefId> {

    List<RagPresetProfileRefEntity> findByPreset_IdOrderByOrdinalAsc(UUID presetId);

    void deleteByPreset_Id(UUID presetId);
}

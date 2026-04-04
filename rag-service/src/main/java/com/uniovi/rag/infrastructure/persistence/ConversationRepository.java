package com.uniovi.rag.infrastructure.persistence;

import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<ConversationEntity, UUID> {

    List<ConversationEntity> findByProject_IdOrderByUpdatedAtDesc(UUID projectId);

    List<ConversationEntity> findByProject_IdAndUser_IdOrderByUpdatedAtDesc(UUID projectId, UUID userId);

    Optional<ConversationEntity> findByIdAndUser_Id(UUID id, UUID userId);

    long countByProject_Id(UUID projectId);
}

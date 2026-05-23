package com.uniovi.rag.infrastructure.persistence;

import com.uniovi.rag.domain.knowledge.IndexSnapshotStatus;
import com.uniovi.rag.domain.knowledge.KnowledgeSnapshotScopeType;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KnowledgeIndexSnapshotRepository extends JpaRepository<KnowledgeIndexSnapshotEntity, UUID> {

    long countByProject_IdAndScopeTypeAndConversationIsNullAndStatus(
            UUID projectId, KnowledgeSnapshotScopeType scopeType, IndexSnapshotStatus status);

    long countByConversation_IdAndScopeTypeAndStatus(
            UUID conversationId, KnowledgeSnapshotScopeType scopeType, IndexSnapshotStatus status);

    List<KnowledgeIndexSnapshotEntity> findByProject_IdAndSignatureHashAndStatusOrderByUpdatedAtDesc(
            UUID projectId, String signatureHash, IndexSnapshotStatus status);

    @Query(
            """
            SELECT s FROM KnowledgeIndexSnapshotEntity s
            WHERE s.project.id = :pid
            AND s.scopeType = :st
            AND s.conversation IS NULL
            AND s.status = :status
            ORDER BY s.updatedAt DESC
            """)
    List<KnowledgeIndexSnapshotEntity> findActiveProjectSnapshots(
            @Param("pid") UUID projectId,
            @Param("st") KnowledgeSnapshotScopeType scopeType,
            @Param("status") IndexSnapshotStatus status);

    @Query(
            """
            SELECT s FROM KnowledgeIndexSnapshotEntity s
            WHERE s.conversation.id = :cid
            AND s.scopeType = :st
            AND s.status = :status
            ORDER BY s.updatedAt DESC
            """)
    List<KnowledgeIndexSnapshotEntity> findActiveConversationSnapshots(
            @Param("cid") UUID conversationId,
            @Param("st") KnowledgeSnapshotScopeType scopeType,
            @Param("status") IndexSnapshotStatus status);

    @Query(
            """
            SELECT s FROM KnowledgeIndexSnapshotEntity s
            WHERE s.project.id = :pid
            AND s.scopeType = :st
            AND s.conversation IS NULL
            ORDER BY s.createdAt DESC
            """)
    List<KnowledgeIndexSnapshotEntity> findByProjectAndScopeProjectOrderByCreatedAtDesc(
            @Param("pid") UUID projectId, @Param("st") KnowledgeSnapshotScopeType scopeType);

    @Query(
            """
            SELECT s FROM KnowledgeIndexSnapshotEntity s
            WHERE s.conversation.id = :cid
            AND s.scopeType = :st
            ORDER BY s.createdAt DESC
            """)
    List<KnowledgeIndexSnapshotEntity> findByConversationAndScopeOrderByCreatedAtDesc(
            @Param("cid") UUID conversationId, @Param("st") KnowledgeSnapshotScopeType scopeType);

    @Query(
            """
            SELECT s FROM KnowledgeIndexSnapshotEntity s
            WHERE s.ownerType = :ownerType
            AND s.ownerId = :ownerId
            ORDER BY s.createdAt DESC
            """)
    List<KnowledgeIndexSnapshotEntity> findByOwnerOrderByCreatedAtDesc(
            @Param("ownerType") com.uniovi.rag.domain.knowledge.KnowledgeSnapshotOwnerType ownerType,
            @Param("ownerId") UUID ownerId);

    @Query(
            """
            SELECT s FROM KnowledgeIndexSnapshotEntity s
            WHERE s.ownerType = :ownerType
            AND s.ownerId = :ownerId
            AND s.status = :status
            ORDER BY s.updatedAt DESC
            """)
    List<KnowledgeIndexSnapshotEntity> findActiveByOwner(
            @Param("ownerType") com.uniovi.rag.domain.knowledge.KnowledgeSnapshotOwnerType ownerType,
            @Param("ownerId") UUID ownerId,
            @Param("status") IndexSnapshotStatus status);

    /**
     * Keeps {@code knowledge_index_snapshot.project_id} aligned with the conversation's project after a move.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value =
                    """
                    UPDATE knowledge_index_snapshot
                    SET project_id = :destProjectId, updated_at = :updatedAt
                    WHERE conversation_id = :conversationId
                      AND scope_type = 'CONVERSATION'
                    """,
            nativeQuery = true)
    int updateProjectIdForConversationSnapshots(
            @Param("conversationId") UUID conversationId,
            @Param("destProjectId") UUID destProjectId,
            @Param("updatedAt") Instant updatedAt);
}

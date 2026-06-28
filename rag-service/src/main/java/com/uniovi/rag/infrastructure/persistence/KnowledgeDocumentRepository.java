package com.uniovi.rag.infrastructure.persistence;

import com.uniovi.rag.domain.ProjectDocumentStatus;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocumentEntity, UUID> {

    @Query(
            """
            SELECT d FROM KnowledgeDocumentEntity d
            JOIN d.project p
            WHERE p.owner.id = :userId
            AND (:projectId IS NULL OR p.id = :projectId)
            AND (:corpusScope IS NULL OR d.corpusScope = :corpusScope)
            AND (:conversationId IS NULL
                OR (d.conversation IS NOT NULL AND d.conversation.id = :conversationId))
            AND (:status IS NULL OR d.status = :status)
            """)
    Page<KnowledgeDocumentEntity> searchForOwner(
            @Param("userId") UUID userId,
            @Param("projectId") UUID projectId,
            @Param("corpusScope") CorpusScope corpusScope,
            @Param("conversationId") UUID conversationId,
            @Param("status") ProjectDocumentStatus status,
            Pageable pageable);

    @Query(
            """
            SELECT COUNT(d) FROM KnowledgeDocumentEntity d JOIN d.project p
            WHERE p.owner.id = :userId
            """)
    long countByProjectOwner_Id(@Param("userId") UUID userId);

    @Query(
            """
            SELECT COALESCE(SUM(d.byteSize), 0) FROM KnowledgeDocumentEntity d JOIN d.project p
            WHERE p.owner.id = :userId AND d.byteSize IS NOT NULL
            """)
    long sumByteSizeByProjectOwner_Id(@Param("userId") UUID userId);

    @Query(
            """
            SELECT d FROM KnowledgeDocumentEntity d
            JOIN FETCH d.project p
            LEFT JOIN FETCH d.conversation
            WHERE p.owner.id = :userId
            """)
    List<KnowledgeDocumentEntity> findAllByProjectOwner_Id(@Param("userId") UUID userId);

    List<KnowledgeDocumentEntity> findByProject_IdOrderByUploadedAtDesc(UUID projectId);

    Optional<KnowledgeDocumentEntity> findFirstByProject_IdAndFileNameAndCorpusScopeAndConversationIsNull(
            UUID projectId, String fileName, CorpusScope corpusScope);

    Optional<KnowledgeDocumentEntity> findFirstByProject_IdAndFileNameAndCorpusScopeAndConversation_Id(
            UUID projectId, String fileName, CorpusScope corpusScope, UUID conversationId);

    Optional<KnowledgeDocumentEntity> findByIdAndProject_Id(UUID id, UUID projectId);

    long countByProject_Id(UUID projectId);

    long countByProject_IdAndStatus(UUID projectId, ProjectDocumentStatus status);

    long countByProject_IdAndIdIn(UUID projectId, List<UUID> ids);

    List<KnowledgeDocumentEntity> findByConversation_IdAndCorpusScope(UUID conversationId, CorpusScope corpusScope);

    List<KnowledgeDocumentEntity> findByProject_IdAndCorpusScopeOrderByIdAsc(UUID projectId, CorpusScope corpusScope);

    List<KnowledgeDocumentEntity> findByStatus(ProjectDocumentStatus status);

    @Modifying
    @Query(
            value = "UPDATE project_documents SET project_id = :destProjectId WHERE conversation_id = :conversationId AND corpus_scope = 'CHAT_LOCAL'",
            nativeQuery = true)
    int updateProjectForChatLocalDocuments(
            @Param("conversationId") UUID conversationId, @Param("destProjectId") UUID destProjectId);
}

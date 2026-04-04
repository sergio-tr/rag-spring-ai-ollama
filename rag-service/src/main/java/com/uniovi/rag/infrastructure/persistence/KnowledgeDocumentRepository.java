package com.uniovi.rag.infrastructure.persistence;

import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocumentEntity, UUID> {

    List<KnowledgeDocumentEntity> findByProject_IdOrderByUploadedAtDesc(UUID projectId);

    Optional<KnowledgeDocumentEntity> findByIdAndProject_Id(UUID id, UUID projectId);

    long countByProject_Id(UUID projectId);

    List<KnowledgeDocumentEntity> findByConversation_IdAndCorpusScope(UUID conversationId, CorpusScope corpusScope);

    @Modifying
    @Query(
            value = "UPDATE project_documents SET project_id = :destProjectId WHERE conversation_id = :conversationId AND corpus_scope = 'CHAT_LOCAL'",
            nativeQuery = true)
    int updateProjectForChatLocalDocuments(
            @Param("conversationId") UUID conversationId, @Param("destProjectId") UUID destProjectId);
}

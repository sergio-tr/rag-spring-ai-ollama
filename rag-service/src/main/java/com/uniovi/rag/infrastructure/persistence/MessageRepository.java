package com.uniovi.rag.infrastructure.persistence;

import com.uniovi.rag.infrastructure.persistence.jpa.MessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<MessageEntity, UUID> {

    List<MessageEntity> findByConversation_IdAndDeletedAtIsNullOrderBySeqAsc(UUID conversationId);

    /**
     * Messages strictly before {@code seq} (exclusive), for P18 replay memory reconstruction.
     */
    List<MessageEntity> findByConversation_IdAndSeqLessThanAndDeletedAtIsNullOrderBySeqAsc(UUID conversationId, int seq);

    @Query("SELECT COALESCE(MAX(m.seq), 0) FROM MessageEntity m WHERE m.conversation.id = :cid")
    int findMaxSeqByConversationId(@Param("cid") UUID conversationId);

    List<MessageEntity> findByConversation_IdAndSeqGreaterThanAndDeletedAtIsNullOrderBySeqAsc(
            UUID conversationId, int seq);

    Optional<MessageEntity> findByConversation_IdAndSeqAndDeletedAtIsNull(UUID conversationId, int seq);
}

package com.uniovi.rag.infrastructure.persistence;

import com.uniovi.rag.infrastructure.persistence.jpa.MessageFeedbackEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MessageFeedbackRepository extends JpaRepository<MessageFeedbackEntity, UUID> {
}

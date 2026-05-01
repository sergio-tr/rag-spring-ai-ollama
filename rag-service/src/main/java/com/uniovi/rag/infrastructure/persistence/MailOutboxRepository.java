package com.uniovi.rag.infrastructure.persistence;

import com.uniovi.rag.infrastructure.persistence.jpa.MailOutboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MailOutboxRepository extends JpaRepository<MailOutboxEntity, UUID> {
    List<MailOutboxEntity> findTop50BySentAtIsNullOrderByCreatedAtAsc();
}


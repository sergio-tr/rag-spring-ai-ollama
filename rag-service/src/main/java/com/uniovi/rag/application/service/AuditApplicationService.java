package com.uniovi.rag.application.service;

import com.uniovi.rag.infrastructure.persistence.AuditLogRepository;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.AuditLogEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Minimal audit trail for configuration changes.
 */
@Service
public class AuditApplicationService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public AuditApplicationService(AuditLogRepository auditLogRepository, UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void persistAuditEntry(
            UUID actorUserId, String action, String resourceType, UUID resourceId, Map<String, Object> payload) {
        Optional<UserEntity> actor = actorUserId != null ? userRepository.findById(actorUserId) : Optional.empty();
        Instant now = Instant.now();
        auditLogRepository.save(
                AuditLogEntity.create(actor.orElse(null), action, resourceType, resourceId, payload, now));
    }
}

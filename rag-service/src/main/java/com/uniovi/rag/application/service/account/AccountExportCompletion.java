package com.uniovi.rag.application.service.account;

import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.service.async.AsyncTaskMutationService;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

/**
 * Parameters for persisting a finished account export ZIP and marking the async task succeeded.
 */
public record AccountExportCompletion(
        AsyncTaskEntity task,
        UUID taskId,
        UUID artifactId,
        UserEntity user,
        Path zipPath,
        String sha256,
        long byteSize,
        Instant createdAt,
        Instant expiresAt,
        AsyncTaskMutationService mutation) {}

package com.uniovi.rag.application.service.knowledge;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * In-memory exclusive lock for project-scoped index operations (rebuild/reindex/snapshot mutation).
 *
 * <p>Scope: single JVM instance only. In multi-instance deployments this must be replaced with a DB-backed
 * lock (e.g. Postgres advisory locks) to avoid split-brain concurrent rebuilds.
 */
@Service
public class ProjectIndexOperationLockService {

    private final Map<UUID, HeldLock> held = new ConcurrentHashMap<>();

    public LockAttempt tryAcquire(UUID projectId, String owner, UUID runId, String reason) {
        if (projectId == null) {
            return LockAttempt.rejected("MISSING_PROJECT_ID", null);
        }
        String o = owner != null && !owner.isBlank() ? owner.trim() : "unknown";
        HeldLock next = new HeldLock(projectId, o, runId, reason, Instant.now());
        HeldLock existing = held.putIfAbsent(projectId, next);
        if (existing != null) {
            return LockAttempt.rejected("ALREADY_LOCKED", existing);
        }
        return LockAttempt.acquired(next);
    }

    public boolean release(UUID projectId, String owner, UUID runId) {
        if (projectId == null) {
            return false;
        }
        HeldLock current = held.get(projectId);
        if (current == null) {
            return false;
        }
        if (!current.matches(owner, runId)) {
            return false;
        }
        return held.remove(projectId, current);
    }

    public boolean isLocked(UUID projectId) {
        return projectId != null && held.containsKey(projectId);
    }

    public HeldLock getLock(UUID projectId) {
        return projectId == null ? null : held.get(projectId);
    }

    public record HeldLock(
            UUID projectId,
            String owner,
            UUID runId,
            String reason,
            Instant acquiredAt) {
        boolean matches(String owner, UUID runId) {
            String o = owner != null ? owner.trim() : null;
            if (o == null || o.isBlank()) {
                o = "unknown";
            }
            return this.owner.equals(o) && (this.runId == null ? runId == null : this.runId.equals(runId));
        }
    }

    public record LockAttempt(
            boolean acquired,
            String rejectionCode,
            HeldLock heldBy) {
        public static LockAttempt acquired(HeldLock lock) {
            return new LockAttempt(true, null, lock);
        }

        public static LockAttempt rejected(String code, HeldLock heldBy) {
            return new LockAttempt(false, code, heldBy);
        }
    }
}


package com.uniovi.rag.infrastructure.persistence;

import com.uniovi.rag.domain.AsyncTaskStatus;
import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AsyncTaskRepository extends JpaRepository<AsyncTaskEntity, UUID> {

    Optional<AsyncTaskEntity> findByIdAndUser_Id(UUID id, UUID userId);

    @Query(
            "SELECT t FROM AsyncTaskEntity t WHERE t.user.id = :userId AND t.taskType = :type AND t.status IN :statuses")
    List<AsyncTaskEntity> findByUser_IdAndTaskTypeAndStatusIn(
            @Param("userId") UUID userId,
            @Param("type") AsyncTaskType type,
            @Param("statuses") Collection<AsyncTaskStatus> statuses);

    List<AsyncTaskEntity> findByStatusInAndUpdatedAtBefore(
            Collection<AsyncTaskStatus> statuses, Instant updatedAtBefore);
}

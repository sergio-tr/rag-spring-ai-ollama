package com.uniovi.rag.infrastructure.persistence;

import com.uniovi.rag.infrastructure.persistence.jpa.ClassifierModelEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClassifierModelRepository extends JpaRepository<ClassifierModelEntity, UUID> {

    List<ClassifierModelEntity> findByOwner_IdOrderByTrainedAtDesc(UUID ownerId);

    Optional<ClassifierModelEntity> findByOwner_IdAndArtifactPath(UUID ownerId, String artifactPath);

    @Query(
            value =
                    "SELECT * FROM classifier_model WHERE owner_id = :ownerId AND (hyperparams->>'sourceTaskId') = :taskId",
            nativeQuery = true)
    Optional<ClassifierModelEntity> findByOwnerIdAndSourceTaskId(@Param("ownerId") UUID ownerId, @Param("taskId") String taskId);

    @Query("SELECT c FROM ClassifierModelEntity c WHERE c.owner.id = :ownerId AND c.active = true")
    List<ClassifierModelEntity> findActiveByOwner_Id(@Param("ownerId") UUID ownerId);
}

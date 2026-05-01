package com.uniovi.rag.infrastructure.persistence.jpa;

import com.uniovi.rag.domain.ClassifierModelStatus;

import java.time.Instant;
import java.util.Map;

/**
 * Constructs {@link ClassifierModelEntity} rows after successful classifier-service training.
 */
public final class ClassifierModelEntityFactory {

    private ClassifierModelEntityFactory() {
    }

    public static ClassifierModelEntity newReadyTrainingArtifact(
            UserEntity owner,
            String displayName,
            String inferenceTag,
            Map<String, Object> hyperparams,
            Instant trainedAt) {
        ClassifierModelEntity e = new ClassifierModelEntity();
        e.setOwner(owner);
        e.setName(displayName);
        e.setDataset(null);
        e.setHyperparams(hyperparams);
        e.setArtifactPath(inferenceTag);
        e.setStatus(ClassifierModelStatus.READY);
        e.setTrainedAt(trainedAt);
        e.setActive(false);
        e.setPassesGate(false);
        return e;
    }
}

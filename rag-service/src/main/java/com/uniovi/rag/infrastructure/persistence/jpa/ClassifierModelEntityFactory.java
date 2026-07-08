package com.uniovi.rag.infrastructure.persistence.jpa;

import com.uniovi.rag.application.service.model.RegisteredModelValidation;
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
        return newReadyTrainingArtifact(owner, displayName, inferenceTag, hyperparams, trainedAt, false);
    }

    /**
     * @param systemCatalogRow when true, allows the configured system inference tag (e.g. {@code default})
     */
    public static ClassifierModelEntity newReadyTrainingArtifact(
            UserEntity owner,
            String displayName,
            String inferenceTag,
            Map<String, Object> hyperparams,
            Instant trainedAt,
            boolean systemCatalogRow) {
        RegisteredModelValidation.assertValidName(displayName);
        RegisteredModelValidation.assertValidInferenceTag(inferenceTag, systemCatalogRow);
        ClassifierModelEntity e = new ClassifierModelEntity();
        e.setOwner(owner);
        e.setName(RegisteredModelValidation.normalizeName(displayName));
        e.setDataset(null);
        e.setHyperparams(hyperparams);
        e.setArtifactPath(RegisteredModelValidation.normalizeInferenceTag(inferenceTag));
        e.setStatus(ClassifierModelStatus.READY);
        e.setTrainedAt(trainedAt);
        e.setActive(false);
        e.setPassesGate(false);
        return e;
    }
}

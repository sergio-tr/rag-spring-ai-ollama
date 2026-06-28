package com.uniovi.rag.infrastructure.persistence.jpa;

import com.uniovi.rag.domain.evaluation.EvaluationCorpusSourceType;
import java.time.Instant;

public final class EvaluationCorpusEntityFactory {

    private EvaluationCorpusEntityFactory() {}

    public static EvaluationCorpusEntity newCorpus(
            UserEntity owner, String name, EvaluationCorpusSourceType sourceType, ProjectEntity indexProject) {
        EvaluationCorpusEntity entity = new EvaluationCorpusEntity();
        entity.setOwner(owner);
        entity.setName(name);
        entity.setSourceType(sourceType);
        entity.setIndexProject(indexProject);
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }
}

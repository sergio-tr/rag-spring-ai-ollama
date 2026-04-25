package com.uniovi.rag.application.service.runtime.query;

import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;

import java.util.Optional;

public interface QueryClassifierAdapter {

    ClassifierOutcome classify(ExecutionContext ctx, String normalizedText);

    record ClassifierOutcome(
            String classifierLabel,
            Optional<QueryType> classifierQueryType,
            ClassifierStatus classifierStatus,
            String classifierModelIdUsed,
            String note) {
        public ClassifierOutcome {
            classifierQueryType = classifierQueryType == null ? Optional.empty() : classifierQueryType;
        }
    }
}


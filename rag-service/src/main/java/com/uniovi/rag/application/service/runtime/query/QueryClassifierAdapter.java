package com.uniovi.rag.application.service.runtime.query;

import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;

import java.util.Objects;
import java.util.Optional;

public interface QueryClassifierAdapter {

    ClassifierOutcome classify(ExecutionContext ctx, String normalizedText);

    record ClassifierOutcome(
            String classifierLabel,
            Optional<QueryType> classifierQueryType,
            ClassifierStatus classifierStatus,
            String classifierModelIdUsed,
            String note,
            Optional<Double> classifierConfidence,
            Optional<String> classifierLabelSetHash) {
        public ClassifierOutcome {
            Objects.requireNonNull(classifierQueryType, "classifierQueryType");
            classifierConfidence = Objects.requireNonNullElseGet(classifierConfidence, Optional::empty);
            classifierLabelSetHash = Objects.requireNonNullElseGet(classifierLabelSetHash, Optional::empty);
        }

        public ClassifierOutcome(
                String classifierLabel,
                Optional<QueryType> classifierQueryType,
                ClassifierStatus classifierStatus,
                String classifierModelIdUsed,
                String note) {
            this(classifierLabel, classifierQueryType, classifierStatus, classifierModelIdUsed, note, Optional.empty(), Optional.empty());
        }
    }
}


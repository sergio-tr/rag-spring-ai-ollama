package com.uniovi.rag.application.service.runtime.query;

import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.query.AmbiguityAssessment;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.NormalizedQuery;
import com.uniovi.rag.domain.runtime.query.StructuredRewriteResult;

import java.util.Optional;

public interface AmbiguityAssessmentService {

    AmbiguityAssessment assess(
            NormalizedQuery normalized,
            Optional<QueryType> classifierQueryType,
            String classifierLabel,
            ClassifierStatus classifierStatus,
            StructuredRewriteResult rewrite,
            EntityExtractionResult entities);
}


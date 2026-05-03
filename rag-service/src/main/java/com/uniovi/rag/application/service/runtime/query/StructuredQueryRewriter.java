package com.uniovi.rag.application.service.runtime.query;

import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.NormalizedQuery;
import com.uniovi.rag.domain.runtime.query.StructuredRewriteResult;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import com.uniovi.rag.domain.model.QueryType;

import java.util.Optional;

public interface StructuredQueryRewriter {

    StructuredRewriteResult rewrite(
            ExecutionContext ctx,
            NormalizedQuery normalized,
            String classifierLabel,
            Optional<QueryType> classifierQueryType,
            ClassifierStatus classifierStatus,
            EntityExtractionResult entities);
}


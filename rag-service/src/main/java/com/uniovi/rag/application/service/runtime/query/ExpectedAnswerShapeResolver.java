package com.uniovi.rag.application.service.runtime.query;

import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.ExpectedAnswerShape;

import java.util.Optional;

public interface ExpectedAnswerShapeResolver {

    ExpectedAnswerShape resolve(Optional<QueryType> classifierQueryType, EntityExtractionResult entities);
}


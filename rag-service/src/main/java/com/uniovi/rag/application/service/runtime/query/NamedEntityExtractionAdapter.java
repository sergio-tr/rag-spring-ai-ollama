package com.uniovi.rag.application.service.runtime.query;

import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;

public interface NamedEntityExtractionAdapter {

    EntityExtractionResult extract(ExecutionContext ctx, String normalizedText);
}


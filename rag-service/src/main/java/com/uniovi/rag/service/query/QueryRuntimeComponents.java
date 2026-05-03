package com.uniovi.rag.service.query;

import com.uniovi.rag.service.query.pipeline.QueryInputPreparer;
import com.uniovi.rag.service.query.pipeline.ResponseSynthesisPipeline;

/**
 * Bundles the query pipeline objects built together for {@link ProcessQueryService} and evaluation factory.
 */
public record QueryRuntimeComponents(
        QueryInputPreparer queryInputPreparer,
        ResponseSynthesisPipeline responseSynthesisPipeline
) {
}

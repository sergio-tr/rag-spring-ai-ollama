package com.uniovi.rag.application.service.runtime.query.expand;

import com.uniovi.rag.application.service.llm.ProviderAwareSecondaryLlmExecutor;

public abstract class AbstractQueryExpander implements QueryExpander {

    protected final ProviderAwareSecondaryLlmExecutor secondaryLlmExecutor;

    public AbstractQueryExpander(ProviderAwareSecondaryLlmExecutor secondaryLlmExecutor) {
        this.secondaryLlmExecutor = secondaryLlmExecutor;
    }
}

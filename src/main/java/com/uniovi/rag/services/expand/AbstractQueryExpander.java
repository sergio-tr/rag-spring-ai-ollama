package com.uniovi.rag.services.expand;

import org.springframework.ai.ollama.OllamaChatModel;

public abstract class AbstractQueryExpander implements QueryExpander {

    protected final OllamaChatModel model;

    public AbstractQueryExpander(OllamaChatModel model) {
        this.model = model;
    }

}

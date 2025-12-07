package com.uniovi.rag.services.expand;

import org.springframework.ai.chat.client.ChatClient;

public abstract class AbstractQueryExpander implements QueryExpander {

    protected final ChatClient client;

    public AbstractQueryExpander(ChatClient client) {
        this.client = client;
    }

}

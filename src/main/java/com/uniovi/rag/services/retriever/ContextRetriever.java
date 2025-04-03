package com.uniovi.rag.services.retriever;

import org.springframework.stereotype.Service;

@Service
public interface ContextRetriever {

    String retrieve(String query);

    String retrieve(String query, String context);
}

package com.uniovi.rag.services.query;

public interface QueryService {

    void setSystemPrompt(String prompt);
    String generateResponse(String question);
}

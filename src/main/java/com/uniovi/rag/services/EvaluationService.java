package com.uniovi.rag.services;

import java.util.List;
import java.util.Map;

public interface EvaluationService {

    Map<String, Object> evaluate();
    void loadData();
    Map<String, String> getQuestionsAndAnswers();

    List<String> getSystemPrompts();
}

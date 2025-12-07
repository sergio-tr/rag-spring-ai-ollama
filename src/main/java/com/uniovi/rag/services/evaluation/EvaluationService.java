package com.uniovi.rag.services.evaluation;

import com.uniovi.rag.model.Loggable;

import java.util.Map;

public interface EvaluationService extends Loggable {

    Map<String, Object> evaluate();

    void loadData();

    Map<String, String> getQuestionsAndAnswers();
}

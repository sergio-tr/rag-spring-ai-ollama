package com.uniovi.rag.services.evaluation;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.model.Loggable;

import java.util.Map;

public interface EvaluationService extends Loggable {

    Map<String, Object> evaluate();
    
    Map<String, Object> evaluateWithConfiguration(RagFeatureConfiguration customConfig);
    Map<String, Map<String, Object>> evaluateAllConfigurations();

    void loadData();

    Map<String, String> getQuestionsAndAnswers();
}

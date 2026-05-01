package com.uniovi.rag.service.evaluation;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagImplementationProperties;
import com.uniovi.rag.infrastructure.observability.Loggable;

import java.util.Map;

public interface EvaluationService extends Loggable {

    Map<String, Object> evaluate();
    
    /**
     * @param customConfig feature flags (expansion, ner, tools, …)
     * @param implementationProperties implementation selection ({@code rag.query-service-impl}, etc.);
     *        use the application bean to mirror the default runtime
     */
    Map<String, Object> evaluateWithConfiguration(
            RagFeatureConfiguration customConfig,
            RagImplementationProperties implementationProperties);

    Map<String, Map<String, Object>> evaluateAllConfigurations();

    void loadData();

    Map<String, String> getQuestionsAndAnswers();

    /** Whether the in-memory evaluation dataset has been loaded for this service instance. */
    boolean isEvaluationDataLoaded();
}

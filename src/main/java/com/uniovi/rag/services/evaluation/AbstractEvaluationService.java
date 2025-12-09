package com.uniovi.rag.services.evaluation;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.services.document.DocumentService;
import com.uniovi.rag.services.query.QueryService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public abstract class AbstractEvaluationService implements EvaluationService {

    protected final ChatClient chatClient;
    protected final DocumentService documentService;
    protected final QueryService queryService;
    protected final RagFeatureConfiguration featureConfig;
    protected boolean dataLoaded = false;
    
    // For dynamic evaluation with custom configurations
    protected EvaluationServiceFactory evaluationServiceFactory;

    protected final static PromptTemplate EVALUATION_PROMPT_TEMPLATE = new PromptTemplate("""
        Act as an expert evaluator of RAG (Retrieval-Augmented Generation) systems. 
        Assess the quality of a generated answer to a question by comparing it with the expected correct answer.
        
        **IMPORTANT**: Do not invent or use any external knowledge. 
        Evaluate only what can be inferred from the three provided inputs: the question, the expected correct answer, and the system-generated answer.
        
        Question: {question}
        Expected Correct Answer: {correctAnswer}
        System-Generated Answer: {generatedAnswer}
        
        Evaluate the following criteria on a scale from 1 to 5:
        
        1. **Correctness**: Is the answer correct based on what was expected?
        2. **Context Sufficiency**: Is it possible to answer correctly with the information provided?
        3. **Relevance**: Does the answer address only what was asked, without digressions?
        4. **Independence**: Can the answer be understood on its own, without relying on additional context?
        
        Respond in this format:
        
        Correctness: [1-5] - Justification: ...
        Context Sufficiency: [1-5] - Justification: ...
        Relevance: [1-5] - Justification: ...
        Independence: [1-5] - Justification: ...
        Overall Summary: [Brief overall assessment of the answer quality]
        """);



    public AbstractEvaluationService(RagFeatureConfiguration featureConfig, ChatClient chatClient, DocumentService documentService, QueryService queryService) {
        this.featureConfig = featureConfig;
        this.chatClient = chatClient;
        this.documentService = documentService;
        this.queryService = queryService;
    }

    @Override
    public void loadData() {
        // Load data with default configuration from application.properties
        loadDataWithConfiguration(featureConfig);
    }
    
    /**
     * Loads data with a specific configuration.
     * If the database already has documents, it clears them first to avoid duplicates.
     * 
     * @param config The configuration to use for loading documents
     */
    public void loadDataWithConfiguration(RagFeatureConfiguration config) {
        // Check if config is different from default
        boolean isCustomConfig = !configsEqual(config, featureConfig);
        
        if (isCustomConfig && evaluationServiceFactory != null) {
            // Always clear and reload for custom configurations
            log().info("Loading documents with custom configuration: {}", config.getConfiguration());
            DocumentService customDocService = evaluationServiceFactory.createDocumentService(config);
            customDocService.clearDatabase();
            loadSpecificDataWithService(customDocService);
            dataLoaded = true;
        } else {
            // Use default configuration
            if (!dataLoaded) {
                if (documentService.hasDocuments()) {
                    log().debug("Database already has documents, skipping load");
                } else {
                    log().debug("Loading documents with default configuration");
                    loadSpecificData();
                }
                dataLoaded = true;
            }
        }
    }
    
    /**
     * Compares two RagFeatureConfiguration objects by comparing their configuration maps.
     */
    private boolean configsEqual(RagFeatureConfiguration config1, RagFeatureConfiguration config2) {
        if (config1 == config2) return true;
        if (config1 == null || config2 == null) return false;
        return config1.getConfiguration().equals(config2.getConfiguration());
    }
    
    /**
     * Loads specific data using a provided document service.
     * This allows loading documents with a custom configuration.
     */
    protected void loadSpecificDataWithService(DocumentService docService) {
        loadSpecificData(); // Default implementation uses HTTP endpoint
        // Subclasses can override to use docService directly
    }

    protected abstract void loadSpecificData();

    @Override
    public Map<String, Object> evaluate() {
        // Use default configuration from application.properties
        return evaluateWithConfiguration(featureConfig);
    }
    
    /**
     * Evaluates with a custom configuration.
     * This allows testing different configuration combinations.
     * Automatically manages document loading: clears database and reloads documents with the custom configuration.
     * 
     * @param customConfig The custom configuration to use
     * @return Evaluation results with the custom configuration
     */
    public Map<String, Object> evaluateWithConfiguration(RagFeatureConfiguration customConfig) {
        if (evaluationServiceFactory == null) {
            throw new IllegalStateException("EvaluationServiceFactory must be set to evaluate with custom configuration");
        }
        
        // Load data with custom configuration (will clear and reload if needed)
        loadDataWithConfiguration(customConfig);
        
        Map<String, Object> results = new HashMap<>();
        results.put("configuration", customConfig.getConfiguration());

        // Create services with custom configuration
        QueryService queryServiceToUse = evaluationServiceFactory.createQueryService(customConfig);

        List<Map<String, Object>> resultsForPrompt = new ArrayList<>();

        for (Map.Entry<String, String> entry : getQuestionsAndAnswers().entrySet()) {
            String question = entry.getKey();
            String correctAnswer = entry.getValue();
            String llmResponse = queryServiceToUse.generateResponse(question);

            String evaluation = evaluateResponse(question, correctAnswer, llmResponse);

            Map<String, Object> result = new HashMap<>();
            result.put("question", question);
            result.put("correct_answer", correctAnswer);
            result.put("generated_answer", llmResponse);
            result.put("llm_evaluation", evaluation);

            resultsForPrompt.add(result);

            log().debug(result.toString());
        }

        results.put("results", resultsForPrompt);
        return results;
    }
    
    /**
     * Evaluates all possible configuration combinations.
     * This generates 2^4 = 16 combinations (for 4 boolean flags: expansion, ner, tools, metadata).
     * 
     * @return Map with configuration name as key and evaluation results as value
     */
    public Map<String, Map<String, Object>> evaluateAllConfigurations() {
        if (evaluationServiceFactory == null) {
            throw new IllegalStateException("EvaluationServiceFactory must be set to evaluate all configurations");
        }
        
        Map<String, Map<String, Object>> allResults = new HashMap<>();
        
        // Generate all combinations of boolean flags
        boolean[] flags = {false, true};
        int configNumber = 0;
        
        for (boolean expansion : flags) {
            for (boolean ner : flags) {
                for (boolean tools : flags) {
                    for (boolean metadata : flags) {
                        RagFeatureConfiguration config = new RagFeatureConfiguration();
                        config.setExpansionEnabled(expansion);
                        config.setNerEnabled(ner);
                        config.setToolsEnabled(tools);
                        config.setMetadataEnabled(metadata);
                        
                        String configName = String.format("config_%02d_expansion_%s_ner_%s_tools_%s_metadata_%s",
                                configNumber++,
                                expansion, ner, tools, metadata);
                        
                        log().debug("Evaluating configuration: {}", configName);
                        Map<String, Object> result = evaluateWithConfiguration(config);
                        allResults.put(configName, result);
                    }
                }
            }
        }
        
        return allResults;
    }
    
    /**
     * Sets the evaluation service factory for dynamic configuration testing.
     */
    public void setEvaluationServiceFactory(EvaluationServiceFactory factory) {
        this.evaluationServiceFactory = factory;
    }

    protected String evaluateResponse(String question, String correctAnswer, String llmResponse) {
        String prompt = EVALUATION_PROMPT_TEMPLATE.create(
                Map.of(
                        "question", question,
                        "correctAnswer", correctAnswer,
                        "generatedAnswer", llmResponse
                )
        ).getContents();

        return chatClient
                .prompt()
                .user(prompt)
                .call()
                .content();
    }
}

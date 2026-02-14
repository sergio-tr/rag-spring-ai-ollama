package com.uniovi.rag.services.evaluation;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.model.QueryResponse;
import com.uniovi.rag.services.document.DocumentService;
import com.uniovi.rag.services.query.QueryService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${evaluation.clean-before-load:true}")
    private boolean cleanBeforeLoad = true;
    
    // For dynamic evaluation with custom configurations
    protected EvaluationServiceFactory evaluationServiceFactory;

    protected final static PromptTemplate EVALUATION_PROMPT_TEMPLATE = new PromptTemplate("""
        Act as an expert evaluator of RAG (Retrieval-Augmented Generation) systems. 
        Assess the quality of a generated answer by determining if it correctly answers the question.
        
        **CRITICAL EVALUATION PRINCIPLES**:
        1. **Focus on the question, not exact word matching**: The "Expected Correct Answer" is only a GUIDE showing what information should be present. The generated answer does NOT need to match it word-for-word.
        2. **Evaluate if the answer responds to the question**: Determine if the generated answer provides the information needed to answer the question, even if it's shorter, longer, or worded differently than the expected answer.
        3. **Key information presence**: Check if the essential information requested by the question is present in the generated answer, regardless of format or additional details.
        4. **Context understanding**: Understand what the question is actually asking for. For example:
           - If asked "Which acta had the longest duration?", the answer should identify the acta (date/identifier), not necessarily include the exact duration.
           - If asked "What was the duration?", the answer should include the duration value.
        
        **IMPORTANT**: Do not invent or use any external knowledge. 
        Evaluate only what can be inferred from the three provided inputs: the question, the expected correct answer (as a guide), and the system-generated answer.
        
        Question: {question}
        Expected Correct Answer (GUIDE ONLY - not required to match exactly): {correctAnswer}
        System-Generated Answer: {generatedAnswer}
        
        Evaluate the following criteria on a scale from 1 to 5:
        
        1. **Correctness**: Does the answer correctly respond to what the question is asking? 
           - Consider if the essential information requested is present, even if formatted differently.
           - Do NOT penalize for missing details that weren't explicitly asked for in the question.
           - Example: If asked "Which acta?", answering with the date is correct, even if duration details are missing.
        
        2. **Context Sufficiency**: Is it possible to answer correctly with the information provided?
        
        3. **Relevance**: Does the answer address what was asked, without unnecessary digressions?
           - A shorter answer that directly answers the question is better than a longer one with irrelevant details.
        
        4. **Independence**: Can the answer be understood on its own, without relying on additional context?
        
        **Scoring Guidelines**:
        - Score 5 if the answer correctly responds to the question, even if it's shorter or worded differently than expected.
        - Score 4-5 if the answer contains the essential information requested, even if some non-essential details are missing.
        - Score 3-4 if the answer is partially correct but missing some important information.
        - Score 1-2 only if the answer is incorrect, irrelevant, or doesn't address the question.
        
        Respond in this format:
        
        Correctness: [1-5] - Justification: [Focus on whether the answer responds to the question, not word matching]
        Context Sufficiency: [1-5] - Justification: ...
        Relevance: [1-5] - Justification: ...
        Independence: [1-5] - Justification: ...
        Overall Summary: [Brief overall assessment focusing on whether the answer correctly responds to the question]
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
            // Use default configuration: optionally clear and reload so BD is coherent with current config
            if (cleanBeforeLoad) {
                log().info("Clearing database and loading documents with default configuration (evaluation.clean-before-load=true)");
                documentService.clearDatabase();
                loadSpecificData();
                dataLoaded = true;
            } else if (!dataLoaded) {
                if (documentService.hasDocuments()) {
                    log().info("Database already has documents, skipping load");
                } else {
                    log().info("Loading documents with default configuration");
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
            QueryResponse queryResponse = queryServiceToUse.generateResponse(question);
            String llmResponse = queryResponse.getAnswer();

            String evaluation = evaluateResponse(question, correctAnswer, llmResponse);

            Map<String, Object> result = new HashMap<>();
            result.put("question", question);
            result.put("correct_answer", correctAnswer);
            result.put("generated_answer", llmResponse);
            result.put("llm_evaluation", evaluation);
            
            // Add tool metadata for traceability
            result.put("tool_used", queryResponse.getToolUsed());
            result.put("query_type", queryResponse.getQueryType() != null ? queryResponse.getQueryType().name() : null);
            result.put("used_tool", queryResponse.isUsedTool());

            resultsForPrompt.add(result);

            log().info("Question: {} | Tool: {} | QueryType: {} | UsedTool: {}", 
                    question, queryResponse.getToolUsed(), queryResponse.getQueryType(), queryResponse.isUsedTool());
            log().info(result.toString());
        }

        results.put("results", resultsForPrompt);
        return results;
    }
    
    /**
     * Evaluates all possible configuration combinations.
     * This generates 2^4 = 16 combinations (for 4 boolean flags: expansion, ner, tools, metadata).
     * For each combination the database is cleared and reloaded so results are coherent with that config.
     * <p>
     * Combination table (expansion × ner × tools × metadata):
     * <ul>
     *   <li>00: F,F,F,F - no expansion, no NER, no tools, no metadata (plain retrieval + askModel)</li>
     *   <li>01: F,F,F,T - metadata only (metadata pipeline, no tools/NER/expansion)</li>
     *   <li>02: F,F,T,F - tools without metadata</li>
     *   <li>03: F,F,T,T - tools + metadata (e.g. results in tools-metadata.json)</li>
     *   <li>04: F,T,F,F - NER only, retriever may not use NER unless 7.1 implemented</li>
     *   <li>05: F,T,F,T - NER + metadata</li>
     *   <li>06: F,T,T,F - NER + tools</li>
     *   <li>07: F,T,T,T - NER + tools + metadata</li>
     *   <li>08-15: same with expansion=true (query expansion; may degrade if LLM expands poorly)</li>
     * </ul>
     * Use GET /evaluate/all to run all 16; use POST /evaluate/custom with body
     * {"expansion":bool,"ner":bool,"tools":bool,"metadata":bool} to test a single combination.
     * Save results per config (e.g. tools-metadata-ner-true.json) for before/after comparison.
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
                        
                        log().info("Evaluating configuration: {}", configName);
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

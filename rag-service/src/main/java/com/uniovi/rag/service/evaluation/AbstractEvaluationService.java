package com.uniovi.rag.service.evaluation;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.model.QueryResponse;
import com.uniovi.rag.service.document.DocumentService;
import com.uniovi.rag.service.query.QueryService;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public abstract class AbstractEvaluationService implements EvaluationService {    
    
    /**
     * Descriptors for each feature flag varied in evaluateAllConfigurations.
     * Order defines bit position: first = bit 0, second = bit 1, etc.
     * To add/remove/reorder flags, edit only this array; the number of combinations is 2^length.
     */
    private static final FlagDescriptor[] FEATURE_FLAG_DESCRIPTORS = {
        new FlagDescriptor("exp", RagFeatureConfiguration::setExpansionEnabled, RagFeatureConfiguration::isExpansionEnabled),
        new FlagDescriptor("ner", RagFeatureConfiguration::setNerEnabled, RagFeatureConfiguration::isNerEnabled),
        new FlagDescriptor("tools", RagFeatureConfiguration::setToolsEnabled, RagFeatureConfiguration::isToolsEnabled),
        new FlagDescriptor("meta", RagFeatureConfiguration::setMetadataEnabled, RagFeatureConfiguration::isMetadataEnabled),
        new FlagDescriptor("reas", RagFeatureConfiguration::setReasoningEnabled, RagFeatureConfiguration::isReasoningEnabled),
        new FlagDescriptor("rank", RagFeatureConfiguration::setRankerEnabled, RagFeatureConfiguration::isRankerEnabled),
        new FlagDescriptor("post", RagFeatureConfiguration::setPostRetrievalEnabled, RagFeatureConfiguration::isPostRetrievalEnabled),
        new FlagDescriptor("tr", RagFeatureConfiguration::setToolRagEnabled, RagFeatureConfiguration::isToolRagEnabled),
        new FlagDescriptor("fc", RagFeatureConfiguration::setFunctionCallingEnabled, RagFeatureConfiguration::isFunctionCallingEnabled),
    };

    /** Descriptor for a single feature flag: label, setter and getter on RagFeatureConfiguration. */
    private static final class FlagDescriptor {
        final String label;
        final BiConsumer<RagFeatureConfiguration, Boolean> setter;
        final Function<RagFeatureConfiguration, Boolean> getter;

        FlagDescriptor(String label,
                    BiConsumer<RagFeatureConfiguration, Boolean> setter,
                    Function<RagFeatureConfiguration, Boolean> getter) {
            this.label = label;
            this.setter = setter;
            this.getter = getter;
        }
    }

    protected final ChatClient chatClient;
    protected final DocumentService documentService;
    protected final QueryService queryService;
    protected final RagFeatureConfiguration featureConfig;
    protected boolean dataLoaded = false;

    protected boolean cleanBeforeLoad = true;
    
    // For dynamic evaluation with custom configurations
    protected EvaluationServiceFactory evaluationServiceFactory;

    protected final static PromptTemplate EVALUATION_PROMPT_TEMPLATE = new PromptTemplate("""
        Act as an expert evaluator of RAG (Retrieval-Augmented Generation) systems. 
        Assess the quality of a generated answer by determining if it correctly answers the question.
        
        **CRITICAL EVALUATION PRINCIPLES (BE STRICT)**:
        1. **Correctness 5 only when fully correct**: Score 5 ONLY if the answer has all key facts correct and adds NO wrong facts. One correct fact plus one wrong fact (e.g. two dates when only one is correct) = at most 4, not 5.
        2. **Lists and enumerated answers**: If the expected answer specifies a set (e.g. one acta, two dates, "ninguna") and the generated answer adds extra or wrong items, Correctness at most 4 (or 3 if more wrong than right). Expected "one date" and generated "date A and date B" with one wrong → not 5.
        3. **"No information found" / "Ninguna"**: If the expected answer says no information was found (e.g. "No se ha encontrado ninguna información", "Ninguna acta") and the generated answer invents or lists content, Correctness MUST be 1 or 2 and Groundedness 1 or 2.
        4. **Yes/No questions**: If the generated answer contradicts the expected Yes/No, Correctness MUST be 1 or 2.
        5. **Comparison questions**: If the conclusion is opposite to the expected (e.g. expected "agosto" but generated "febrero"), Correctness at most 2 or 3.
        6. **Context understanding**: If asked for a specific fact (e.g. which acta, duration), the answer must contain that information; partial or wrong set of items reduces the score.
        
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
        
        5. **Groundedness (Fidelity)**: Does the answer rely only on the provided context, without inventing facts? Score 1–5 (1 = invented/unsupported, 5 = fully grounded in context).
        
        **Strict Scoring Guidelines**:
        - Score 5 ONLY when the answer is fully correct: all key facts present, no wrong or extra facts added. One correct fact plus one wrong fact = at most 4.
        - If the expected answer lists a specific number of items (e.g. one acta, two dates) and the generated answer includes extra or wrong items, Correctness at most 4 (or 3 if more wrong than right).
        - If expected says "none"/"ninguna"/"no information found" and generated invents or lists content, Correctness 1 or 2, Groundedness 1 or 2.
        - Yes/No contradiction → Correctness 1 or 2.
        - Score 4 only when essential information is correct and at most minor, non-contradictory extras.
        - Score 3 when partially correct but with missing or wrong important information.
        - Score 1-2 when incorrect, contradictory, irrelevant, or inventing content.
        
        Respond in this format:
        
        Correctness: [1-5] - Justification: [Focus on whether the answer responds to the question, not word matching]
        Context Sufficiency: [1-5] - Justification: ...
        Relevance: [1-5] - Justification: ...
        Independence: [1-5] - Justification: ...
        Groundedness: [1-5] - Justification: [Whether the answer relies only on context without inventing facts]
        Overall Summary: [Brief overall assessment focusing on whether the answer correctly responds to the question]
        """);

    // --- LLM evaluation parsing and metric computation ---
    protected static final int BLEU_MAX_N = 4;
    protected static final int DEFAULT_K = 5;
    protected static final Pattern CORRECTNESS_LINE = Pattern.compile(
            "Correctness\\s*:\\s*\\[?\\s*([1-5])\\.?\\d*\\s*\\]?",
            Pattern.CASE_INSENSITIVE
    );
    protected static final Pattern CONTEXT_SUFFICIENCY_LINE = Pattern.compile(
            "Context\\s+Sufficiency\\s*:\\s*\\[?\\s*([1-5])\\.?\\d*\\s*\\]?",
            Pattern.CASE_INSENSITIVE
    );
    protected static final Pattern RELEVANCE_LINE = Pattern.compile(
            "Relevance\\s*:\\s*\\[?\\s*([1-5])\\.?\\d*\\s*\\]?",
            Pattern.CASE_INSENSITIVE
    );
    protected static final Pattern INDEPENDENCE_LINE = Pattern.compile(
            "Independence\\s*:\\s*\\[?\\s*([1-5])\\.?\\d*\\s*\\]?",
            Pattern.CASE_INSENSITIVE
    );
    protected static final Pattern GROUNDEDNESS_LINE = Pattern.compile(
            "Groundedness\\s*:\\s*\\[?\\s*([1-5])\\.?\\d*\\s*\\]?",
            Pattern.CASE_INSENSITIVE
    );

    public AbstractEvaluationService(
        RagFeatureConfiguration featureConfig, 
        ChatClient chatClient, 
        DocumentService documentService,
        QueryService queryService,
        boolean cleanBeforeLoad
    ) {
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
            String question = entry.getKey() != null ? entry.getKey() : "";
            String correctAnswer = entry.getValue() != null ? entry.getValue() : "";
            QueryResponse queryResponse = queryServiceToUse.generateResponse(question);
            String llmResponse = queryResponse != null && queryResponse.getAnswer() != null ? queryResponse.getAnswer() : "";

            String evaluation = evaluateResponse(question, correctAnswer, llmResponse);

            Map<String, Object> result = new HashMap<>();
            result.put("question", question);
            result.put("correct_answer", correctAnswer);
            result.put("generated_answer", llmResponse);
            result.put("llm_evaluation", evaluation);
            
            // Add tool metadata for traceability
            result.put("tool_used", queryResponse != null ? queryResponse.getToolUsed() : null);
            result.put("query_type", queryResponse != null && queryResponse.getQueryType() != null ? queryResponse.getQueryType().name() : null);
            result.put("used_tool", queryResponse != null && queryResponse.isUsedTool());

            resultsForPrompt.add(result);

            log().info(
                "Question: {} | Tool: {} | QueryType: {} | UsedTool: {}", 
                question, queryResponse != null ? queryResponse.getToolUsed() : null, 
                queryResponse != null ? queryResponse.getQueryType() : null, 
                queryResponse != null && queryResponse.isUsedTool()
            );
            log().info(result.toString());
        }

        results.put("results", resultsForPrompt);
        // F.3, F.4: Build evaluation_summary from parsed llm_evaluation scores
        Map<String, Object> evaluationSummary = buildEvaluationSummary(resultsForPrompt);
        results.put("evaluation_summary", evaluationSummary);
        return results;
    }

    /**
     * Evaluates all possible configuration combinations of the main feature flags.
     * For each combination the database is cleared and reloaded so results are coherent with that config.
     *
     * @return Map with configuration name as key and evaluation results as value
     */
    public Map<String, Map<String, Object>> evaluateAllConfigurations() {
        if (evaluationServiceFactory == null) {
            throw new IllegalStateException("EvaluationServiceFactory must be set to evaluate all configurations");
        }
        Map<String, Map<String, Object>> allResults = new HashMap<>();
        int totalConfigs = 1 << FEATURE_FLAG_DESCRIPTORS.length;

        for (int configIndex = 0; configIndex < totalConfigs; configIndex++) {
            RagFeatureConfiguration config = buildFeatureConfigFromIndex(configIndex);
            String configName = buildFeatureConfigName(configIndex, config);
            log().info("Evaluating configuration: {}", configName);
            Map<String, Object> result = evaluateWithConfiguration(config);
            allResults.put(configName, result);
        }
        return allResults;
    }

    /**
     * Builds a RagFeatureConfiguration from a bit mask index (0 .. 2^N - 1, N = number of flag descriptors).
     * Bit 0 = first flag in FEATURE_FLAG_DESCRIPTORS, bit 1 = second, etc.
     */
    private static RagFeatureConfiguration buildFeatureConfigFromIndex(int configIndex) {
        RagFeatureConfiguration config = new RagFeatureConfiguration();
        config.setUseRetrieval(true);
        config.setUseAdvisor(true);
        for (int i = 0; i < FEATURE_FLAG_DESCRIPTORS.length; i++) {
            boolean value = (configIndex & (1 << i)) != 0;
            FEATURE_FLAG_DESCRIPTORS[i].setter.accept(config, value);
        }
        return config;
    }

    /**
     * Builds a short name for the configuration (e.g. config_000_exp_false_ner_true_...).
     */
    private static String buildFeatureConfigName(int configIndex, RagFeatureConfiguration config) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("config_%03d", configIndex));
        for (FlagDescriptor d : FEATURE_FLAG_DESCRIPTORS) {
            sb.append("_").append(d.label).append("_").append(Boolean.TRUE.equals(d.getter.apply(config)));
        }
        return sb.toString();
    }
    
    /**
     * Sets the evaluation service factory for dynamic configuration testing.
     */
    public void setEvaluationServiceFactory(EvaluationServiceFactory factory) {
        this.evaluationServiceFactory = factory;
    }

    protected String evaluateResponse(String question, String correctAnswer, String llmResponse) {
        // Map.of does not accept null; use empty string to avoid NPE when response is missing (e.g. after LLM failure or config change)
        String q = question != null ? question : "";
        String ca = correctAnswer != null ? correctAnswer : "";
        String gen = llmResponse != null ? llmResponse : "";
        String prompt = EVALUATION_PROMPT_TEMPLATE.create(
                Map.of(
                        "question", q,
                        "correctAnswer", ca,
                        "generatedAnswer", gen
                )
        ).getContents();

        return chatClient
                .prompt()
                .user(prompt)
                .call()
                .content();
    }

    // --- LLM evaluation parsing and metric computation (moved from LlmEvaluationParser) ---

    /**
     * Parses a single llm_evaluation text block and returns a map with keys
     * correctness, context_sufficiency, relevance, independence, groundedness (Integer 1-5 or null if not found).
     */
    protected Map<String, Integer> parseScores(String llmEvaluation) {
        Map<String, Integer> scores = new HashMap<>();
        if (llmEvaluation == null || llmEvaluation.isBlank()) {
            return scores;
        }
        scores.put("correctness", extractScore(llmEvaluation, CORRECTNESS_LINE));
        scores.put("context_sufficiency", extractScore(llmEvaluation, CONTEXT_SUFFICIENCY_LINE));
        scores.put("relevance", extractScore(llmEvaluation, RELEVANCE_LINE));
        scores.put("independence", extractScore(llmEvaluation, INDEPENDENCE_LINE));
        scores.put("groundedness", extractScore(llmEvaluation, GROUNDEDNESS_LINE));
        return scores;
    }

    protected Integer extractScore(String text, Pattern pattern) {
        Matcher m = pattern.matcher(text);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * Builds evaluation_summary from a list of per-question results.
     * Each result map should contain "llm_evaluation" (String).
     * Returns a map with structure: generation (mean_*, pct_correctness_ge_4, bleu, rouge_l, meteor), retrieval (mean_context_sufficiency, precision_at_k, recall_at_k, mrr when data available).
     */
    protected Map<String, Object> buildEvaluationSummary(List<Map<String, Object>> results) {
        Map<String, Object> summary = new HashMap<>();
        Map<String, Object> generation = new HashMap<>();

        List<Integer> correctness = new ArrayList<>();
        List<Integer> contextSufficiency = new ArrayList<>();
        List<Integer> relevance = new ArrayList<>();
        List<Integer> independence = new ArrayList<>();
        List<Integer> groundedness = new ArrayList<>();

        for (Map<String, Object> r : results) {
            Object evalObj = r.get("llm_evaluation");
            String evalText = evalObj instanceof String ? (String) evalObj : (evalObj != null ? evalObj.toString() : null);
            Map<String, Integer> scores = parseScores(evalText);
            addIfNotNull(correctness, scores.get("correctness"));
            addIfNotNull(contextSufficiency, scores.get("context_sufficiency"));
            addIfNotNull(relevance, scores.get("relevance"));
            addIfNotNull(independence, scores.get("independence"));
            addIfNotNull(groundedness, scores.get("groundedness"));
        }

        generation.put("mean_correctness", mean(correctness));
        generation.put("mean_context_sufficiency", mean(contextSufficiency));
        generation.put("mean_relevance", mean(relevance));
        generation.put("mean_independence", mean(independence));
        generation.put("mean_groundedness", mean(groundedness));

        int total = correctness.size();
        long ge4 = correctness.stream().filter(s -> s != null && s >= 4).count();
        generation.put("pct_correctness_ge_4", total > 0 ? (100.0 * ge4 / total) : null);
        generation.put("n_parsed", total);

        Double bleu = computeBLEU(results);
        if (bleu != null) generation.put("bleu", bleu);
        Double rougeL = computeRougeL(results);
        if (rougeL != null) generation.put("rouge_l", rougeL);
        Double meteor = computeMETEOR(results);
        if (meteor != null) generation.put("meteor", meteor);

        summary.put("generation", generation);

        Map<String, Object> retrieval = new HashMap<>();
        Object ctxSuff = generation.get("mean_context_sufficiency");
        if (ctxSuff != null) retrieval.put("mean_context_sufficiency", ctxSuff);
        Double precisionAtK = computePrecisionAtK(results, DEFAULT_K);
        if (precisionAtK != null) retrieval.put("precision_at_k", precisionAtK);
        Double recallAtK = computeRecallAtK(results, DEFAULT_K);
        if (recallAtK != null) retrieval.put("recall_at_k", recallAtK);
        Double mrr = computeMRR(results);
        if (mrr != null) retrieval.put("mrr", mrr);
        summary.put("retrieval", retrieval);
        return summary;
    }

    protected void addIfNotNull(List<Integer> list, Integer value) {
        if (value != null) list.add(value);
    }

    protected Double mean(List<Integer> values) {
        if (values == null || values.isEmpty()) return null;
        double sum = 0;
        for (Integer v : values) {
            if (v != null) sum += v;
        }
        return sum / values.size();
    }

    protected List<String> tokenize(String text) {
        if (text == null || text.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String w : text.toLowerCase().trim().split("\\s+")) {
            String t = w.replaceAll("^[^a-záéíóúñ0-9]+|[^a-záéíóúñ0-9]+$", "");
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    protected Double computeBLEU(List<Map<String, Object>> results) {
        if (results == null || results.isEmpty()) return null;
        double sum = 0;
        int count = 0;
        for (Map<String, Object> r : results) {
            String ref = getString(r, "correct_answer");
            String hyp = getString(r, "generated_answer");
            if (ref == null && hyp == null) continue;
            List<String> refTok = tokenize(ref != null ? ref : "");
            List<String> hypTok = tokenize(hyp != null ? hyp : "");
            double score = bleuScore(refTok, hypTok);
            if (score >= 0) { sum += score; count++; }
        }
        return count > 0 ? sum / count : null;
    }

    protected double bleuScore(List<String> ref, List<String> hyp) {
        if (hyp.isEmpty()) return ref.isEmpty() ? 1.0 : 0.0;
        double bp = ref.size() >= hyp.size() ? 1.0 : Math.exp(1.0 - (double) ref.size() / hyp.size());
        double logSum = 0;
        for (int n = 1; n <= BLEU_MAX_N; n++) {
            if (hyp.size() < n) continue;
            int matches = 0;
            Map<String, Integer> refCount = ngramCounts(ref, n);
            Map<String, Integer> hypCount = ngramCounts(hyp, n);
            for (Map.Entry<String, Integer> e : hypCount.entrySet()) {
                matches += Math.min(e.getValue(), refCount.getOrDefault(e.getKey(), 0));
            }
            int hypN = hypCount.values().stream().mapToInt(Integer::intValue).sum();
            if (hypN == 0) continue;
            double pn = (double) matches / hypN;
            if (pn > 0) logSum += Math.log(pn) / BLEU_MAX_N;
        }
        return bp * (logSum <= 0 ? 0 : Math.exp(logSum));
    }

    protected Map<String, Integer> ngramCounts(List<String> tokens, int n) {
        Map<String, Integer> m = new HashMap<>();
        for (int i = 0; i <= tokens.size() - n; i++) {
            String ng = String.join(" ", tokens.subList(i, i + n));
            m.merge(ng, 1, Integer::sum);
        }
        return m;
    }

    protected Double computeRougeL(List<Map<String, Object>> results) {
        if (results == null || results.isEmpty()) return null;
        double sum = 0;
        int count = 0;
        for (Map<String, Object> r : results) {
            String ref = getString(r, "correct_answer");
            String hyp = getString(r, "generated_answer");
            if (ref == null && hyp == null) continue;
            List<String> refTok = tokenize(ref != null ? ref : "");
            List<String> hypTok = tokenize(hyp != null ? hyp : "");
            double score = rougeLScore(refTok, hypTok);
            if (score >= 0) { sum += score; count++; }
        }
        return count > 0 ? sum / count : null;
    }

    protected double rougeLScore(List<String> ref, List<String> hyp) {
        if (ref.isEmpty() && hyp.isEmpty()) return 1.0;
        if (ref.isEmpty() || hyp.isEmpty()) return 0.0;
        int lcs = lcsLength(ref, hyp);
        double p = (double) lcs / hyp.size();
        double r = (double) lcs / ref.size();
        return (p + r > 0) ? 2.0 * p * r / (p + r) : 0.0;
    }

    protected int lcsLength(List<String> a, List<String> b) {
        int n = a.size(), m = b.size();
        int[][] dp = new int[n + 1][m + 1];
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                if (a.get(i - 1).equals(b.get(j - 1)))
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                else
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
            }
        }
        return dp[n][m];
    }

    protected Double computeMETEOR(List<Map<String, Object>> results) {
        if (results == null || results.isEmpty()) return null;
        double sum = 0;
        int count = 0;
        for (Map<String, Object> r : results) {
            String ref = getString(r, "correct_answer");
            String hyp = getString(r, "generated_answer");
            if (ref == null && hyp == null) continue;
            List<String> refTok = tokenize(ref != null ? ref : "");
            List<String> hypTok = tokenize(hyp != null ? hyp : "");
            double score = meteorScore(refTok, hypTok);
            if (score >= 0) { sum += score; count++; }
        }
        return count > 0 ? sum / count : null;
    }

    protected double meteorScore(List<String> ref, List<String> hyp) {
        if (ref.isEmpty() && hyp.isEmpty()) return 1.0;
        if (ref.isEmpty() || hyp.isEmpty()) return 0.0;
        Set<String> refSet = new HashSet<>(ref);
        Set<String> hypSet = new HashSet<>(hyp);
        int matches = 0;
        for (String w : hypSet) {
            if (refSet.contains(w)) matches++;
        }
        double p = (double) matches / hyp.size();
        double r = (double) matches / ref.size();
        double fMean = (p + r > 0) ? 10.0 * p * r / (9.0 * p + r) : 0.0;
        int chunks = countChunks(ref, hyp);
        double penalty = 0.5 * Math.pow(chunks / (double) Math.max(matches, 1), 3);
        return (1.0 - penalty) * fMean;
    }

    protected int countChunks(List<String> ref, List<String> hyp) {
        boolean[] matchedRef = new boolean[ref.size()];
        int[] align = new int[hyp.size()];
        java.util.Arrays.fill(align, -1);
        for (int i = 0; i < hyp.size(); i++) {
            for (int j = 0; j < ref.size(); j++) {
                if (!matchedRef[j] && ref.get(j).equals(hyp.get(i))) {
                    align[i] = j;
                    matchedRef[j] = true;
                    break;
                }
            }
        }
        int chunks = 0;
        int prev = -2;
        for (int j : align) {
            if (j >= 0) {
                if (j != prev + 1) chunks++;
                prev = j;
            }
        }
        return Math.max(chunks, 1);
    }

    protected String getString(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return null;
        return v.toString().trim();
    }

    protected Double computePrecisionAtK(List<Map<String, Object>> results, int k) {
        if (results == null || results.isEmpty()) return null;
        double sum = 0;
        int count = 0;
        for (Map<String, Object> r : results) {
            List<String> retrieved = getStringList(r, "retrieved_document_ids");
            List<String> relevant = getStringList(r, "relevant_document_ids");
            if (retrieved == null || retrieved.isEmpty() || relevant == null || relevant.isEmpty()) continue;
            Set<String> relSet = new HashSet<>(relevant);
            int atK = Math.min(k, retrieved.size());
            if (atK == 0) continue;
            long hits = retrieved.subList(0, atK).stream().filter(relSet::contains).count();
            sum += (double) hits / atK;
            count++;
        }
        return count > 0 ? sum / count : null;
    }

    protected Double computeRecallAtK(List<Map<String, Object>> results, int k) {
        if (results == null || results.isEmpty()) return null;
        double sum = 0;
        int count = 0;
        for (Map<String, Object> r : results) {
            List<String> retrieved = getStringList(r, "retrieved_document_ids");
            List<String> relevant = getStringList(r, "relevant_document_ids");
            if (retrieved == null || retrieved.isEmpty() || relevant == null || relevant.isEmpty()) continue;
            Set<String> relSet = new HashSet<>(relevant);
            int atK = Math.min(k, retrieved.size());
            if (atK == 0) continue;
            long hits = retrieved.subList(0, atK).stream().filter(relSet::contains).count();
            sum += (double) hits / relSet.size();
            count++;
        }
        return count > 0 ? sum / count : null;
    }

    protected Double computeMRR(List<Map<String, Object>> results) {
        if (results == null || results.isEmpty()) return null;
        double sum = 0;
        int count = 0;
        for (Map<String, Object> r : results) {
            List<String> retrieved = getStringList(r, "retrieved_document_ids");
            List<String> relevant = getStringList(r, "relevant_document_ids");
            if (retrieved == null || retrieved.isEmpty() || relevant == null || relevant.isEmpty()) continue;
            Set<String> relSet = new HashSet<>(relevant);
            for (int i = 0; i < retrieved.size(); i++) {
                if (relSet.contains(retrieved.get(i))) {
                    sum += 1.0 / (i + 1);
                    count++;
                    break;
                }
            }
        }
        return count > 0 ? sum / count : null;
    }

    protected List<String> getStringList(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return null;
        if (v instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object o : list) {
                if (o != null) out.add(o.toString());
            }
            return out;
        }
        return null;
    }
}

package com.uniovi.rag.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;
import java.util.HashMap;

@ConfigurationProperties(prefix = "rag.features")
public class RagFeatureConfiguration {

    private boolean expansionEnabled;
    private boolean nerEnabled;
    private boolean toolsEnabled;
    private boolean metadataEnabled;
    private boolean reasoningEnabled;
    private boolean rankerEnabled;
    private boolean postRetrievalEnabled;
    private boolean toolRagEnabled;
    private boolean functionCallingEnabled;
    private boolean useRetrieval = true;
    private boolean useAdvisor = true;
    /** Optional override for evaluation: query-service-impl (simple, simple-process, process). */
    private String queryServiceImpl;
    /** Optional override for evaluation: retriever-impl (basic, filtered, minute-document). */
    private String retrieverImpl;
    /** Optional override for evaluation: analyser-impl (minute-ner, no-op). */
    private String analyserImpl;

    public boolean isExpansionEnabled() { return expansionEnabled; }
    public void setExpansionEnabled(boolean expansionEnabled) { this.expansionEnabled = expansionEnabled; }

    public boolean isToolsEnabled() { return toolsEnabled; }
    public void setToolsEnabled(boolean toolsEnabled) { this.toolsEnabled = toolsEnabled; }

    public boolean isNerEnabled() { return nerEnabled; }
    public void setNerEnabled(boolean nerEnabled) { this.nerEnabled = nerEnabled; }

    public boolean isMetadataEnabled() { return metadataEnabled; }
    public void setMetadataEnabled(boolean metadataEnabled) { this.metadataEnabled = metadataEnabled; }

    public boolean isReasoningEnabled() { return reasoningEnabled; }
    public void setReasoningEnabled(boolean reasoningEnabled) { this.reasoningEnabled = reasoningEnabled; }

    public boolean isRankerEnabled() { return rankerEnabled; }
    public void setRankerEnabled(boolean rankerEnabled) { this.rankerEnabled = rankerEnabled; }

    public boolean isPostRetrievalEnabled() { return postRetrievalEnabled; }
    public void setPostRetrievalEnabled(boolean postRetrievalEnabled) { this.postRetrievalEnabled = postRetrievalEnabled; }

    public boolean isToolRagEnabled() { return toolRagEnabled; }
    public void setToolRagEnabled(boolean toolRagEnabled) { this.toolRagEnabled = toolRagEnabled; }

    public boolean isFunctionCallingEnabled() { return functionCallingEnabled; }
    public void setFunctionCallingEnabled(boolean functionCallingEnabled) { this.functionCallingEnabled = functionCallingEnabled; }

    public boolean isUseRetrieval() { return useRetrieval; }
    public void setUseRetrieval(boolean useRetrieval) { this.useRetrieval = useRetrieval; }

    public boolean isUseAdvisor() { return useAdvisor; }
    public void setUseAdvisor(boolean useAdvisor) { this.useAdvisor = useAdvisor; }

    public String getQueryServiceImpl() { return queryServiceImpl; }
    public void setQueryServiceImpl(String queryServiceImpl) { this.queryServiceImpl = queryServiceImpl; }
    public String getRetrieverImpl() { return retrieverImpl; }
    public void setRetrieverImpl(String retrieverImpl) { this.retrieverImpl = retrieverImpl; }
    public String getAnalyserImpl() { return analyserImpl; }
    public void setAnalyserImpl(String analyserImpl) { this.analyserImpl = analyserImpl; }

    public Map<String, Boolean> getConfiguration() {
        Map<String, Boolean> config = new HashMap<>();
        config.put("expansion", expansionEnabled);
        config.put("ner", nerEnabled);
        config.put("tools", toolsEnabled);
        config.put("metadata", metadataEnabled);
        config.put("reasoning", reasoningEnabled);
        config.put("ranker", rankerEnabled);
        config.put("post-retrieval", postRetrievalEnabled);
        config.put("tool-rag", toolRagEnabled);
        config.put("function-calling", functionCallingEnabled);
        config.put("use-retrieval", useRetrieval);
        config.put("use-advisor", useAdvisor);
        return config;
    }
}

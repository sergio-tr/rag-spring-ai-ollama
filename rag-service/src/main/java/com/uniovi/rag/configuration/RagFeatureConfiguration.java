package com.uniovi.rag.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;
import java.util.HashMap;

/**
 * Boolean feature flags under {@code rag.features.*}.
 * Implementation selection (process/simple, retriever, analyser) is in {@link RagImplementationProperties}.
 * <p><b>Tools</b>: two related flags — {@link #functionCallingEnabled} (Spring AI {@code ChatClient.tools(adapter)})
 * and {@link #toolsEnabled} (manual tools: deterministic adapter + {@link RagToolsConfiguration} registry).
 * Order: function-calling first when enabled; if it does not apply or fails, {@code tools} enables the manual path.
 * {@code metadata} still selects metadata vs non-metadata tool implementations on both paths.</p>
 */
@ConfigurationProperties(prefix = "rag.features")
public class RagFeatureConfiguration {

    private boolean expansionEnabled;
    private boolean nerEnabled;
    /** Manual tools: deterministic tools path in {@link com.uniovi.rag.application.service.runtime.RagExecutionOrchestrator} + {@link com.uniovi.rag.configuration.RagToolsConfiguration} registry. */
    private boolean toolsEnabled;
    private boolean metadataEnabled;
    private boolean reasoningEnabled;
    private boolean rankerEnabled;
    private boolean postRetrievalEnabled;
    /** Spring AI function calling: {@code .tools(MeetingMinutesToolsAdapter)}. When enabled, takes precedence over the manual adapter path. */
    private boolean functionCallingEnabled;
    private boolean useRetrieval = true;
    private boolean useAdvisor = true;
    /** P11 clarification loop core (default off). */
    private boolean clarificationEnabled;
    /** P12 conversational memory stage (default off). */
    private boolean memoryEnabled;
    /** P13 adaptive routing core (default off). */
    private boolean adaptiveRoutingEnabled;

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

    public boolean isFunctionCallingEnabled() { return functionCallingEnabled; }
    public void setFunctionCallingEnabled(boolean functionCallingEnabled) { this.functionCallingEnabled = functionCallingEnabled; }

    public boolean isUseRetrieval() { return useRetrieval; }
    public void setUseRetrieval(boolean useRetrieval) { this.useRetrieval = useRetrieval; }

    public boolean isUseAdvisor() { return useAdvisor; }
    public void setUseAdvisor(boolean useAdvisor) { this.useAdvisor = useAdvisor; }

    public boolean isClarificationEnabled() {
        return clarificationEnabled;
    }

    public void setClarificationEnabled(boolean clarificationEnabled) {
        this.clarificationEnabled = clarificationEnabled;
    }

    public boolean isMemoryEnabled() {
        return memoryEnabled;
    }

    public void setMemoryEnabled(boolean memoryEnabled) {
        this.memoryEnabled = memoryEnabled;
    }

    public boolean isAdaptiveRoutingEnabled() {
        return adaptiveRoutingEnabled;
    }

    public void setAdaptiveRoutingEnabled(boolean adaptiveRoutingEnabled) {
        this.adaptiveRoutingEnabled = adaptiveRoutingEnabled;
    }

    public Map<String, Boolean> getConfiguration() {
        Map<String, Boolean> config = new HashMap<>();
        config.put("expansion", expansionEnabled);
        config.put("ner", nerEnabled);
        config.put("tools", toolsEnabled);
        config.put("metadata", metadataEnabled);
        config.put("reasoning", reasoningEnabled);
        config.put("ranker", rankerEnabled);
        config.put("post-retrieval", postRetrievalEnabled);
        config.put("function-calling", functionCallingEnabled);
        config.put("use-retrieval", useRetrieval);
        config.put("use-advisor", useAdvisor);
        config.put("clarification", clarificationEnabled);
        config.put("memory", memoryEnabled);
        config.put("adaptive-routing", adaptiveRoutingEnabled);
        return config;
    }
}

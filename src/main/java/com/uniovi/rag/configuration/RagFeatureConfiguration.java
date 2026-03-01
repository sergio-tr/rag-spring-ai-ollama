package com.uniovi.rag.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.HashMap;

@Component
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
        return config;
    }
}

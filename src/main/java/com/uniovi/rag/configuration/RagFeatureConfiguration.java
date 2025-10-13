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
    private boolean validationEnabled;
    private boolean reasoningEnabled;
    private boolean metadataEnabled;
    
    // NUEVOS FLAGS DE CACHE Y OPTIMIZACIÓN
    private boolean cacheEnabled = false;
    private boolean cacheDocumentsEnabled = false;
    private boolean cacheMetadataEnabled = false;
    private boolean cacheNerEnabled = false;
    private boolean parallelProcessingEnabled = false;
    private boolean adaptivePromptsEnabled = false;

    public boolean isExpansionEnabled() { return expansionEnabled; }
    public void setExpansionEnabled(boolean expansionEnabled) { this.expansionEnabled = expansionEnabled; }

    public boolean isToolsEnabled() { return toolsEnabled; }
    public void setToolsEnabled(boolean toolsEnabled) { this.toolsEnabled = toolsEnabled; }

    public boolean isValidationEnabled() { return validationEnabled; }
    public void setValidationEnabled(boolean validationEnabled) { this.validationEnabled = validationEnabled; }

    public boolean isNerEnabled() { return nerEnabled; }
    public void setNerEnabled(boolean nerEnabled) { this.nerEnabled = nerEnabled; }

    public boolean isReasoningEnabled() { return reasoningEnabled; }
    public void setReasoningEnabled(boolean reasoningEnabled) { this.reasoningEnabled = reasoningEnabled; }

    public boolean isMetadataEnabled() { return metadataEnabled; }
    public void setMetadataEnabled(boolean metadataEnabled) { this.metadataEnabled = metadataEnabled; }

    // GETTERS Y SETTERS PARA NUEVOS FLAGS
    public boolean isCacheEnabled() { return cacheEnabled; }
    public void setCacheEnabled(boolean cacheEnabled) { this.cacheEnabled = cacheEnabled; }

    public boolean isCacheDocumentsEnabled() { return cacheDocumentsEnabled; }
    public void setCacheDocumentsEnabled(boolean cacheDocumentsEnabled) { this.cacheDocumentsEnabled = cacheDocumentsEnabled; }

    public boolean isCacheMetadataEnabled() { return cacheMetadataEnabled; }
    public void setCacheMetadataEnabled(boolean cacheMetadataEnabled) { this.cacheMetadataEnabled = cacheMetadataEnabled; }

    public boolean isCacheNerEnabled() { return cacheNerEnabled; }
    public void setCacheNerEnabled(boolean cacheNerEnabled) { this.cacheNerEnabled = cacheNerEnabled; }

    public boolean isParallelProcessingEnabled() { return parallelProcessingEnabled; }
    public void setParallelProcessingEnabled(boolean parallelProcessingEnabled) { this.parallelProcessingEnabled = parallelProcessingEnabled; }

    public boolean isAdaptivePromptsEnabled() { return adaptivePromptsEnabled; }
    public void setAdaptivePromptsEnabled(boolean adaptivePromptsEnabled) { this.adaptivePromptsEnabled = adaptivePromptsEnabled; }

    public Map<String, Boolean> getConfiguration() {
        Map<String, Boolean> config = new HashMap<>();
        config.put("expansion", expansionEnabled);
        config.put("ner", nerEnabled);
        config.put("tools", toolsEnabled);
        config.put("validation", validationEnabled);
        config.put("reasoning", reasoningEnabled);
        config.put("metadata", metadataEnabled);
        config.put("cache", cacheEnabled);
        config.put("cache_documents", cacheDocumentsEnabled);
        config.put("cache_metadata", cacheMetadataEnabled);
        config.put("cache_ner", cacheNerEnabled);
        config.put("parallel_processing", parallelProcessingEnabled);
        config.put("adaptive_prompts", adaptivePromptsEnabled);
        return config;
    }
}

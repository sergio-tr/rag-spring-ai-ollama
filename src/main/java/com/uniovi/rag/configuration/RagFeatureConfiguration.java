package com.uniovi.rag.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "rag.features")
public class RagFeatureConfiguration {

    private boolean expansionEnabled;
    private boolean nerEnabled;
    private boolean toolsEnabled;
    private boolean validationEnabled;
    private boolean reasoningEnabled;
    private boolean metadataEnabled;

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

    public Map<String, Boolean> getConfiguration() {
        return Map.of(
                "expansion", expansionEnabled,
                "ner", nerEnabled,
                "tools", toolsEnabled,
                "validation", validationEnabled,
                "reasoning", reasoningEnabled,
                "metadata", metadataEnabled
        );
    }
}

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

    public boolean isExpansionEnabled() { return expansionEnabled; }
    public void setExpansionEnabled(boolean expansionEnabled) { this.expansionEnabled = expansionEnabled; }

    public boolean isToolsEnabled() { return toolsEnabled; }
    public void setToolsEnabled(boolean toolsEnabled) { this.toolsEnabled = toolsEnabled; }

    public boolean isNerEnabled() { return nerEnabled; }
    public void setNerEnabled(boolean nerEnabled) { this.nerEnabled = nerEnabled; }

    public boolean isMetadataEnabled() { return metadataEnabled; }
    public void setMetadataEnabled(boolean metadataEnabled) { this.metadataEnabled = metadataEnabled; }

    public Map<String, Boolean> getConfiguration() {
        Map<String, Boolean> config = new HashMap<>();
        config.put("expansion", expansionEnabled);
        config.put("ner", nerEnabled);
        config.put("tools", toolsEnabled);
        config.put("metadata", metadataEnabled);
        return config;
    }
}

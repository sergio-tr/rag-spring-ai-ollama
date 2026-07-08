package com.uniovi.rag.configuration;

import com.uniovi.rag.tool.metadata.MetadataCorpusScanSettings;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class MetadataCorpusScanConfigurer {

    private final RagRuntimeProperties ragRuntimeProperties;

    public MetadataCorpusScanConfigurer(RagRuntimeProperties ragRuntimeProperties) {
        this.ragRuntimeProperties = ragRuntimeProperties;
    }

    @PostConstruct
    void apply() {
        MetadataCorpusScanSettings.setFullScanMaxDocuments(
                ragRuntimeProperties.getMetadata().getFullScanMaxDocuments());
    }
}

package com.uniovi.rag.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Runtime classifier reliability thresholds. */
@ConfigurationProperties(prefix = "rag.classifier")
public class RagClassifierProperties {

    /** Minimum softmax confidence required to treat a classifier label as authoritative. */
    private double confidenceThreshold = 0.55;

    public double getConfidenceThreshold() {
        return confidenceThreshold;
    }

    public void setConfidenceThreshold(double confidenceThreshold) {
        this.confidenceThreshold = confidenceThreshold;
    }
}

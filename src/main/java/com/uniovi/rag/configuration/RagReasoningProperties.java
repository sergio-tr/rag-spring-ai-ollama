package com.uniovi.rag.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "rag.reasoning")
public class RagReasoningProperties {

    private String strategy = "SIMPLE";
    private int maxSteps = 3;
    private int maxTokensPerStep = 512;

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public int getMaxSteps() {
        return maxSteps;
    }

    public void setMaxSteps(int maxSteps) {
        this.maxSteps = maxSteps;
    }

    public int getMaxTokensPerStep() {
        return maxTokensPerStep;
    }

    public void setMaxTokensPerStep(int maxTokensPerStep) {
        this.maxTokensPerStep = maxTokensPerStep;
    }
}

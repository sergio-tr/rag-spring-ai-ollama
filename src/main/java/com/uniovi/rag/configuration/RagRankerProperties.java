package com.uniovi.rag.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag.ranker")
public class RagRankerProperties {

    private String strategy = "LLM_AS_JUDGE";
    private int candidatesCount = 3;
    private int maxCandidates = 10;
    private boolean alwaysRun = false;

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public int getCandidatesCount() {
        return candidatesCount;
    }

    public void setCandidatesCount(int candidatesCount) {
        this.candidatesCount = candidatesCount;
    }

    public int getMaxCandidates() {
        return maxCandidates;
    }

    public void setMaxCandidates(int maxCandidates) {
        this.maxCandidates = maxCandidates;
    }

    public boolean isAlwaysRun() {
        return alwaysRun;
    }

    public void setAlwaysRun(boolean alwaysRun) {
        this.alwaysRun = alwaysRun;
    }
}

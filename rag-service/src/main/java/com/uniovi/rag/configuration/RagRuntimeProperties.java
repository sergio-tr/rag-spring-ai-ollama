package com.uniovi.rag.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG runtime: workflow versioning, legacy switches, and memory caps for product chat.
 */
@ConfigurationProperties(prefix = "rag.runtime")
public class RagRuntimeProperties {

    /**
     * When {@code true}, restores pre-B3 behaviour where {@link org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor}
     * could run even if post-retrieval was enabled (not recommended). Default {@code false}: post-retrieval forces manual retrieval.
     */
    private boolean legacyAdvisorWithPostRetrieval = false;

    /**
     * Semver of the execution stage graph; included in Lab/eval payloads for reproducibility.
     */
    private String workflowSchemaVersion = "1.0.0";

    /**
     * Max conversation turns injected into prompt when {@code FULL_PRODUCT} memory policy applies (inclusive cap).
     */
    private int memoryMaxTurns = 20;

    /**
     * Max characters of history text injected when {@code FULL_PRODUCT} applies.
     */
    private int memoryMaxChars = 8000;

    public boolean isLegacyAdvisorWithPostRetrieval() {
        return legacyAdvisorWithPostRetrieval;
    }

    public void setLegacyAdvisorWithPostRetrieval(boolean legacyAdvisorWithPostRetrieval) {
        this.legacyAdvisorWithPostRetrieval = legacyAdvisorWithPostRetrieval;
    }

    public String getWorkflowSchemaVersion() {
        return workflowSchemaVersion;
    }

    public void setWorkflowSchemaVersion(String workflowSchemaVersion) {
        this.workflowSchemaVersion = workflowSchemaVersion;
    }

    public int getMemoryMaxTurns() {
        return memoryMaxTurns;
    }

    public void setMemoryMaxTurns(int memoryMaxTurns) {
        this.memoryMaxTurns = memoryMaxTurns;
    }

    public int getMemoryMaxChars() {
        return memoryMaxChars;
    }

    public void setMemoryMaxChars(int memoryMaxChars) {
        this.memoryMaxChars = memoryMaxChars;
    }
}

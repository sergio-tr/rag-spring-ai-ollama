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

    /** Shared prompt/context budgets to prevent model context-window 400s. */
    private Context context = new Context();

    public static final class Context {
        /** Global safety cap for prompt assembly (chars). */
        private int maxPromptChars = 24_000;
        /** Max context chars allowed for full-corpus (P1) context block before prompt wrapping. */
        private int fullCorpusMaxChars = 20_000;
        /** Max context chars for legacy (non-orchestrated) RAG prompt assembly. */
        private int legacyContextMaxChars = 12_000;
        /** Max chars per combined document when grouping chunks into a single Document for legacy retrieval. */
        private int combinedDocumentMaxChars = 12_000;
        /** Max chars of candidate answer text injected into judge prompts. */
        private int judgeMaxAnswerChars = 4_000;

        public int getMaxPromptChars() {
            return maxPromptChars;
        }

        public void setMaxPromptChars(int maxPromptChars) {
            this.maxPromptChars = maxPromptChars;
        }

        public int getFullCorpusMaxChars() {
            return fullCorpusMaxChars;
        }

        public void setFullCorpusMaxChars(int fullCorpusMaxChars) {
            this.fullCorpusMaxChars = fullCorpusMaxChars;
        }

        public int getLegacyContextMaxChars() {
            return legacyContextMaxChars;
        }

        public void setLegacyContextMaxChars(int legacyContextMaxChars) {
            this.legacyContextMaxChars = legacyContextMaxChars;
        }

        public int getCombinedDocumentMaxChars() {
            return combinedDocumentMaxChars;
        }

        public void setCombinedDocumentMaxChars(int combinedDocumentMaxChars) {
            this.combinedDocumentMaxChars = combinedDocumentMaxChars;
        }

        public int getJudgeMaxAnswerChars() {
            return judgeMaxAnswerChars;
        }

        public void setJudgeMaxAnswerChars(int judgeMaxAnswerChars) {
            this.judgeMaxAnswerChars = judgeMaxAnswerChars;
        }
    }

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

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context = context;
    }
}

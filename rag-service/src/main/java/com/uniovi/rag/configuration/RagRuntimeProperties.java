package com.uniovi.rag.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG runtime: workflow versioning, optional advisor override, and memory caps for product chat.
 */
@ConfigurationProperties(prefix = "rag.runtime")
public class RagRuntimeProperties {

    /**
     * When {@code true}, allows {@link org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor}
     * even if post-retrieval is enabled (not recommended). Default {@code false}: post-retrieval forces manual retrieval.
     */
    private boolean advisorWithPostRetrieval = false;

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
        /** Max context chars for workflow prompt assembly (packed context budget). */
        private int workflowContextMaxChars = 12_000;
        /** Max chars per combined document when grouping chunks into a single Document for retrieval. */
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

        public int getWorkflowContextMaxChars() {
            return workflowContextMaxChars;
        }

        public void setWorkflowContextMaxChars(int workflowContextMaxChars) {
            this.workflowContextMaxChars = workflowContextMaxChars;
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

    public boolean isAdvisorWithPostRetrieval() {
        return advisorWithPostRetrieval;
    }

    public void setAdvisorWithPostRetrieval(boolean advisorWithPostRetrieval) {
        this.advisorWithPostRetrieval = advisorWithPostRetrieval;
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

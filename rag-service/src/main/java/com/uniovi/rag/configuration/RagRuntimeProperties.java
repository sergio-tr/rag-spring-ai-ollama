package com.uniovi.rag.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/** Runtime tuning for chat orchestration (memory caps, context budgets, secondary model routing). */
@ConfigurationProperties(prefix = "rag.runtime")
public class RagRuntimeProperties {

    private String workflowSchemaVersion = "1.0.0";
    private boolean advisorWithPostRetrieval = false;
    private int memoryMaxTurns = 20;
    private int memoryMaxChars = 8000;
    private String secondaryModel = "";

    @NestedConfigurationProperty private Context context = new Context();

    @NestedConfigurationProperty private Metadata metadata = new Metadata();

    public String getWorkflowSchemaVersion() {
        return workflowSchemaVersion;
    }

    public void setWorkflowSchemaVersion(String workflowSchemaVersion) {
        this.workflowSchemaVersion = workflowSchemaVersion;
    }

    public boolean isAdvisorWithPostRetrieval() {
        return advisorWithPostRetrieval;
    }

    public void setAdvisorWithPostRetrieval(boolean advisorWithPostRetrieval) {
        this.advisorWithPostRetrieval = advisorWithPostRetrieval;
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

    public String getSecondaryModel() {
        return secondaryModel;
    }

    public void setSecondaryModel(String secondaryModel) {
        this.secondaryModel = secondaryModel != null ? secondaryModel : "";
    }

    public boolean hasSecondaryModel() {
        return secondaryModel != null && !secondaryModel.isBlank();
    }

    public String effectiveSecondaryModel() {
        return hasSecondaryModel() ? secondaryModel.trim() : "";
    }

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context = context != null ? context : new Context();
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata != null ? metadata : new Metadata();
    }

    public static class Metadata {
        private int fullScanMaxDocuments = 30;

        public int getFullScanMaxDocuments() {
            return fullScanMaxDocuments;
        }

        public void setFullScanMaxDocuments(int fullScanMaxDocuments) {
            this.fullScanMaxDocuments = fullScanMaxDocuments;
        }
    }

    public static class Context {
        private int maxPromptChars = 24_000;
        private int fullCorpusMaxChars = 20_000;
        private int workflowContextMaxChars = 12_000;
        private int combinedDocumentMaxChars = 12_000;
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

        public void setStatelessContextMaxChars(int statelessContextMaxChars) {
            this.workflowContextMaxChars = statelessContextMaxChars;
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
}

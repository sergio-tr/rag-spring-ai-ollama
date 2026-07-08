package com.uniovi.rag.domain.evaluation.workbook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parsed experimental workbook: optional sections depending on which sheets exist.
 * {@link #sheetNamesPresent} lists physical sheet names found in the XLSX (for required-sheet checks).
 */
public final class EvaluationWorkbook {

    private final List<String> sheetNamesPresent;
    private final List<ReadmeEntry> readme;
    private final List<CorpusDocument> corpusDocuments;
    private final List<ChunkRegistryEntry> chunkRegistry;
    private final List<LlmReaderQuestion> llmReaderQuestions;
    private final List<LlmRoleEvalCase> llmRoleEvalCases;
    private final List<EmbeddingRetrievalQuery> embeddingRetrievalQueries;
    private final List<RagPresetQuestion> ragPresetQuestionsEnriched;
    private final List<ModelCandidate> llmCandidates;
    private final List<EmbeddingCandidate> embeddingCandidates;
    private final List<RagPresetDefinition> ragPresetCatalog;
    private final List<MetricSpec> metricSpec;
    private final List<ResultSchemaField> resultSchema;
    private final List<SummaryCountRow> summaryCounts;
    private final List<ClassifierQuestionRow> classifierQuestions;

    private EvaluationWorkbook(Builder b) {
        this.sheetNamesPresent = List.copyOf(b.sheetNamesPresent);
        this.readme = List.copyOf(b.readme);
        this.corpusDocuments = List.copyOf(b.corpusDocuments);
        this.chunkRegistry = List.copyOf(b.chunkRegistry);
        this.llmReaderQuestions = List.copyOf(b.llmReaderQuestions);
        this.llmRoleEvalCases = List.copyOf(b.llmRoleEvalCases);
        this.embeddingRetrievalQueries = List.copyOf(b.embeddingRetrievalQueries);
        this.ragPresetQuestionsEnriched = List.copyOf(b.ragPresetQuestionsEnriched);
        this.llmCandidates = List.copyOf(b.llmCandidates);
        this.embeddingCandidates = List.copyOf(b.embeddingCandidates);
        this.ragPresetCatalog = List.copyOf(b.ragPresetCatalog);
        this.metricSpec = List.copyOf(b.metricSpec);
        this.resultSchema = List.copyOf(b.resultSchema);
        this.summaryCounts = List.copyOf(b.summaryCounts);
        this.classifierQuestions = List.copyOf(b.classifierQuestions);
    }

    public List<String> sheetNamesPresent() {
        return sheetNamesPresent;
    }

    public List<ReadmeEntry> readme() {
        return readme;
    }

    public List<CorpusDocument> corpusDocuments() {
        return corpusDocuments;
    }

    public List<ChunkRegistryEntry> chunkRegistry() {
        return chunkRegistry;
    }

    public List<LlmReaderQuestion> llmReaderQuestions() {
        return llmReaderQuestions;
    }

    public List<LlmRoleEvalCase> llmRoleEvalCases() {
        return llmRoleEvalCases;
    }

    public List<EmbeddingRetrievalQuery> embeddingRetrievalQueries() {
        return embeddingRetrievalQueries;
    }

    public List<RagPresetQuestion> ragPresetQuestionsEnriched() {
        return ragPresetQuestionsEnriched;
    }

    public List<ModelCandidate> llmCandidates() {
        return llmCandidates;
    }

    public List<EmbeddingCandidate> embeddingCandidates() {
        return embeddingCandidates;
    }

    public List<RagPresetDefinition> ragPresetCatalog() {
        return ragPresetCatalog;
    }

    public List<MetricSpec> metricSpec() {
        return metricSpec;
    }

    public List<ResultSchemaField> resultSchema() {
        return resultSchema;
    }

    public List<SummaryCountRow> summaryCounts() {
        return summaryCounts;
    }

    public List<ClassifierQuestionRow> classifierQuestions() {
        return classifierQuestions;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<String> sheetNamesPresent = new ArrayList<>();
        private final List<ReadmeEntry> readme = new ArrayList<>();
        private final List<CorpusDocument> corpusDocuments = new ArrayList<>();
        private final List<ChunkRegistryEntry> chunkRegistry = new ArrayList<>();
        private final List<LlmReaderQuestion> llmReaderQuestions = new ArrayList<>();
        private final List<LlmRoleEvalCase> llmRoleEvalCases = new ArrayList<>();
        private final List<EmbeddingRetrievalQuery> embeddingRetrievalQueries = new ArrayList<>();
        private final List<RagPresetQuestion> ragPresetQuestionsEnriched = new ArrayList<>();
        private final List<ModelCandidate> llmCandidates = new ArrayList<>();
        private final List<EmbeddingCandidate> embeddingCandidates = new ArrayList<>();
        private final List<RagPresetDefinition> ragPresetCatalog = new ArrayList<>();
        private final List<MetricSpec> metricSpec = new ArrayList<>();
        private final List<ResultSchemaField> resultSchema = new ArrayList<>();
        private final List<SummaryCountRow> summaryCounts = new ArrayList<>();
        private final List<ClassifierQuestionRow> classifierQuestions = new ArrayList<>();

        public Builder sheetNamesPresent(List<String> names) {
            sheetNamesPresent.clear();
            sheetNamesPresent.addAll(names);
            return this;
        }

        public Builder readme(List<ReadmeEntry> v) {
            readme.clear();
            readme.addAll(v);
            return this;
        }

        public Builder corpusDocuments(List<CorpusDocument> v) {
            corpusDocuments.clear();
            corpusDocuments.addAll(v);
            return this;
        }

        public Builder chunkRegistry(List<ChunkRegistryEntry> v) {
            chunkRegistry.clear();
            chunkRegistry.addAll(v);
            return this;
        }

        public Builder llmReaderQuestions(List<LlmReaderQuestion> v) {
            llmReaderQuestions.clear();
            llmReaderQuestions.addAll(v);
            return this;
        }

        public Builder llmRoleEvalCases(List<LlmRoleEvalCase> v) {
            llmRoleEvalCases.clear();
            llmRoleEvalCases.addAll(v);
            return this;
        }

        public Builder embeddingRetrievalQueries(List<EmbeddingRetrievalQuery> v) {
            embeddingRetrievalQueries.clear();
            embeddingRetrievalQueries.addAll(v);
            return this;
        }

        public Builder ragPresetQuestionsEnriched(List<RagPresetQuestion> v) {
            ragPresetQuestionsEnriched.clear();
            ragPresetQuestionsEnriched.addAll(v);
            return this;
        }

        public Builder llmCandidates(List<ModelCandidate> v) {
            llmCandidates.clear();
            llmCandidates.addAll(v);
            return this;
        }

        public Builder embeddingCandidates(List<EmbeddingCandidate> v) {
            embeddingCandidates.clear();
            embeddingCandidates.addAll(v);
            return this;
        }

        public Builder ragPresetCatalog(List<RagPresetDefinition> v) {
            ragPresetCatalog.clear();
            ragPresetCatalog.addAll(v);
            return this;
        }

        public Builder metricSpec(List<MetricSpec> v) {
            metricSpec.clear();
            metricSpec.addAll(v);
            return this;
        }

        public Builder resultSchema(List<ResultSchemaField> v) {
            resultSchema.clear();
            resultSchema.addAll(v);
            return this;
        }

        public Builder summaryCounts(List<SummaryCountRow> v) {
            summaryCounts.clear();
            summaryCounts.addAll(v);
            return this;
        }

        public Builder classifierQuestions(List<ClassifierQuestionRow> v) {
            classifierQuestions.clear();
            classifierQuestions.addAll(v);
            return this;
        }

        public EvaluationWorkbook build() {
            return new EvaluationWorkbook(this);
        }
    }
}
